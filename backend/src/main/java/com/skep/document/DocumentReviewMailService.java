package com.skep.document;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.company.CompanyType;
import com.skep.document.dto.SendDocumentReviewMailRequest;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 공급사가 자기 회사 자원(장비/인원)을 골라, 각 자원의 서류를 자원별 zip 으로 묶어
 * 임의 이메일(미가입 외부 검토자 포함)로 보내고, BP사를 지정하면 그 BP사 계정 수신함에도 등록한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentReviewMailService {

    private final DocumentRepository docs;
    private final DocumentZipService zipService;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final DocumentReviewRepository reviewRepo;
    private final DocumentReviewItemRepository reviewItemRepo;
    private final CompanyRepository companyRepo;
    private final CompanyService companyService;
    private final NotificationService notifications;
    private final UserRepository userRepo;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** 네이버/지메일 SMTP 는 From 이 반드시 인증 계정 본인이어야 함. */
    @Value("${MAIL_USERNAME:}")
    private String defaultFrom;

    /** 심사 메일 "웹에서 확인" 링크 base — 서명메일과 동일 프로퍼티(dev=http://localhost:5185). */
    @Value("${SKEP_PUBLIC_BASE_URL:http://localhost:8082}")
    private String publicBaseUrl;

    public record ReviewMailResult(int recipients, int resources, int totalDocs, boolean bpDelivered) {}

    private record Resource(OwnerType type, Long ownerId, String label, int docCount, Long supplierId) {}

    @Transactional
    public ReviewMailResult send(SendDocumentReviewMailRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 보낼 수 있습니다");
        }

        List<String> emails = req.emails() == null ? List.of()
                : req.emails().stream().map(String::trim).filter(this::isSafeHeader).distinct().toList();
        boolean hasBp = req.bpCompanyId() != null;
        if (emails.isEmpty() && !hasBp) {
            throw ApiException.badRequest("NO_TARGET", "받는 사람 이메일 또는 BP사를 선택하세요");
        }
        if (hasBp) {
            Company bp = companyRepo.findById(req.bpCompanyId()).orElseThrow(() ->
                    ApiException.badRequest("BP_NOT_FOUND", "선택한 BP사를 찾을 수 없습니다"));
            if (bp.getType() != CompanyType.BP) {
                throw ApiException.badRequest("NOT_BP_COMPANY", "BP사가 아닌 회사로는 보낼 수 없습니다");
            }
        }

        boolean isAdmin = actor.role() == Role.ADMIN;
        Long companyId = actor.companyId();
        // V77 대칭: 병합PDF 모드(DocumentBundlePdfService)와 동일하게 본인 + 직속 자식(협력사) 자원까지 허용.
        // companyId null 이면 scope 빈 목록 → 비ADMIN 은 아래에서 403.
        List<Long> scope = isAdmin ? List.of() : companyService.selfAndChildren(companyId);

        List<Resource> resources = new ArrayList<>();

        for (Long eqId : nullSafe(req.equipmentIds())) {
            Equipment e = equipmentRepo.findById(eqId).orElse(null);
            if (e == null) continue;
            if (!isAdmin && !scope.contains(e.getSupplierId())) {
                throw ApiException.forbidden("NOT_MY_EQUIPMENT", "자기 회사(협력사 포함) 장비만 보낼 수 있습니다");
            }
            String label = e.getVehicleNo() != null ? e.getVehicleNo()
                    : (e.getModel() != null ? e.getModel() : "장비" + eqId);
            int n = docs.findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType.EQUIPMENT, eqId).size();
            if (n > 0) resources.add(new Resource(OwnerType.EQUIPMENT, eqId, label, n, e.getSupplierId()));
        }

        for (Long pId : nullSafe(req.personIds())) {
            Person p = personRepo.findById(pId).orElse(null);
            if (p == null) continue;
            if (!isAdmin && !scope.contains(p.getSupplierId())) {
                throw ApiException.forbidden("NOT_MY_PERSON", "자기 회사(협력사 포함) 인원만 보낼 수 있습니다");
            }
            String label = p.getName() != null ? p.getName() : "인원" + pId;
            int n = docs.findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType.PERSON, pId).size();
            if (n > 0) resources.add(new Resource(OwnerType.PERSON, pId, label, n, p.getSupplierId()));
        }

        if (resources.isEmpty()) {
            throw ApiException.badRequest("NO_DOCS", "선택한 자원에 보낼 서류가 없습니다");
        }

        int totalDocs = resources.stream().mapToInt(Resource::docCount).sum();

        // BP사 계정 수신함 등록 (+ 알림). 메일보다 먼저 저장 — 메일 실패 시 전체 롤백으로 중복 발송 방지.
        boolean bpDelivered = false;
        if (hasBp) {
            Long senderCompanyId = companyId != null ? companyId
                    : resources.stream().map(Resource::supplierId).filter(Objects::nonNull).findFirst().orElse(null);
            if (senderCompanyId != null) {
                DocumentReview review = reviewRepo.save(new DocumentReview(senderCompanyId, req.bpCompanyId(),
                        req.message() != null && !req.message().isBlank() ? req.message().trim() : null,
                        actor.id()));
                List<DocumentReviewItem> items = new ArrayList<>();
                for (Resource r : resources) {
                    items.add(new DocumentReviewItem(review.getId(), r.type(), r.ownerId(), r.label(), r.docCount()));
                }
                reviewItemRepo.saveAll(items);
                notifications.sendToCompany(req.bpCompanyId(),
                        "DOCUMENT_REVIEW", "서류 심사 도착",
                        "자원 " + resources.size() + "건 / 서류 " + totalDocs + "건",
                        "DOCUMENT_REVIEW", review.getId(), null, notifications.senderLabelOf(actor));
                bpDelivered = true;
            } else {
                log.warn("문서 심사 수신함 등록 불가 — 발신 회사를 특정할 수 없음");
            }
        }

        // 이메일 발송 — 이메일이 입력된 경우에만. 실패 시 예외로 위 저장까지 롤백된다.
        if (!emails.isEmpty()) {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                throw ApiException.badRequest("MAIL_DISABLED", "메일 발송이 설정되지 않았습니다");
            }
            // BP사 지정 시 그 회사 소속 사용자 이메일을 자동 CC.
            List<String> ccEmails = hasBp ? bpCcEmails(req.bpCompanyId(), emails) : List.of();
            sendEmail(mailSender, emails, ccEmails, req.message(), resources, totalDocs, actor.email());
        }

        return new ReviewMailResult(emails.size(), resources.size(), totalDocs, bpDelivered);
    }

    /** BP 회사 소속 사용자 이메일 — 심사 메일 자동 CC 대상. to 에 이미 있는 주소는 제외. */
    private List<String> bpCcEmails(Long bpCompanyId, List<String> to) {
        if (bpCompanyId == null) return List.of();
        return userRepo.findByCompanyIdOrderByIdAsc(bpCompanyId).stream()
                .map(User::getEmail)
                .filter(e -> e != null && isSafeHeader(e.trim()))
                .map(String::trim)
                .filter(e -> !to.contains(e))
                .distinct()
                .toList();
    }

    private void sendEmail(JavaMailSender mailSender, List<String> emails, List<String> ccEmails, String message,
                           List<Resource> resources, int totalDocs, String replyTo) {
        record Attachment(String name, byte[] bytes) {}
        List<Attachment> attachments = new ArrayList<>();
        for (Resource r : resources) {
            DocumentZipService.ZipResult zip = zipService.zipSingleResource(r.type(), r.ownerId());
            if (zip != null) attachments.add(new Attachment(safeFileName(r.label()) + ".zip", zip.bytes()));
        }
        if (attachments.isEmpty()) {
            log.warn("review mail aborted — 첨부 zip 생성 전멸");
            throw ApiException.badRequest("ZIP_FAIL", "첨부할 서류 압축 생성에 실패했습니다");
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            if (defaultFrom != null && !defaultFrom.isBlank()) helper.setFrom(defaultFrom);
            // 답장은 발송자(로그인 이메일)에게 — SMTP From 은 인증 계정 고정이라 Reply-To 로 회신 경로 지정.
            if (replyTo != null && isSafeHeader(replyTo) && !replyTo.equalsIgnoreCase(defaultFrom)) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(emails.toArray(new String[0]));
            if (ccEmails != null && !ccEmails.isEmpty()) helper.setCc(ccEmails.toArray(new String[0]));
            helper.setSubject("[서류 검토] 자원 " + attachments.size() + "건 / 서류 " + totalDocs + "건");
            StringBuilder body = new StringBuilder();
            if (message != null && !message.isBlank()) {
                body.append(message.trim()).append("\n\n");
            }
            body.append("첨부된 자원별 압축파일(zip)을 확인해 주세요.\n");
            for (Resource r : resources) {
                body.append(" - ").append(r.label()).append(" (서류 ").append(r.docCount()).append("건)\n");
            }
            // 수신 BP 는 웹 수신함에서 인라인 열람·승인/반려 가능.
            body.append("\n웹에서 바로 확인: ").append(publicBaseUrl).append("/document-reviews/received\n");
            helper.setText(body.toString());
            for (Attachment a : attachments) {
                helper.addAttachment(a.name(), new ByteArrayResource(a.bytes()));
            }
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("review mail send failed: {}", e.getMessage());
            throw ApiException.badRequest("MAIL_FAIL", "메일 발송에 실패했습니다");
        }
    }

    private List<Long> nullSafe(List<Long> l) {
        return l == null ? List.of() : l.stream().distinct().toList();
    }

    /** 이메일 헤더 인젝션 방어 — CRLF/제어문자 / @ 누락 차단. */
    private boolean isSafeHeader(String email) {
        return com.skep.common.SafeText.isSafeEmail(email);
    }

    /** 파일명 안전화 — 경로구분/제어문자 제거. */
    private String safeFileName(String s) {
        return com.skep.common.SafeText.sanitizeFileName(s);
    }
}
