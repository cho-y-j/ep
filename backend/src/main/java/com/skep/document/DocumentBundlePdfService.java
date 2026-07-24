package com.skep.document;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.skep.collection.PdfMergeService;
import com.skep.common.ApiException;
import com.skep.common.SafeText;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.document.dto.ReviewBundlePdfRequest;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentService;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 장비 1대 + 그 교대조 조종원의 서류를 장비별 병합 PDF 한 개로 묶어 이메일/BP사로 발송한다.
 * 각 자원 내부는 현재 유효본(chain head)만 DocumentType.sort_order 순으로, 자원마다 구분면 1페이지를 앞에 끼운다.
 * 산출물은 저장하지 않고 즉석 생성. BP사는 기존 DocumentReview 봉투 이력으로만 남는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentBundlePdfService {

    private final DocumentRepository docs;
    private final DocumentTypeRepository docTypes;
    private final FileStorage storage;
    private final PdfMergeService pdfMerge;
    private final DocumentZipService zipService;
    private final EquipmentService equipmentService;
    private final PersonRepository personRepo;
    private final CompanyRepository companyRepo;
    private final DocumentReviewRepository reviewRepo;
    private final DocumentReviewItemRepository reviewItemRepo;
    private final NotificationService notifications;
    private final UserRepository userRepo;
    private final ReviewMailComposer composer;

    /** 구분면 한글 폰트(Nanum) 바이트 캐시 — 자원마다 재로드하지 않게. */
    private volatile byte[] fontBytes;

    public record BundlePdfResult(int bundles, int recipients, boolean bpDelivered, int totalDocs, int skippedEmpty) {}

    /** fileName = "차량번호_조종원이름(들)" — 첨부 PDF 파일명용(label 은 본문 목록용 차량번호). */
    private record BuiltBundle(String label, String fileName, byte[] pdf, List<EnvelopeItem> resources, int docCount) {}

    private record EnvelopeItem(OwnerType type, Long ownerId, String label, int docCount) {}

    /** includeZip 시 같은 메일에 함께 첨부하는 자원별 압축. */
    private record ZipFile(String name, byte[] bytes) {}

    @Transactional
    public BundlePdfResult send(ReviewBundlePdfRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 보낼 수 있습니다");
        }
        List<ReviewBundlePdfRequest.Bundle> bundles = req.bundles() == null ? List.of() : req.bundles();
        if (bundles.isEmpty()) {
            throw ApiException.badRequest("NO_BUNDLE", "보낼 장비 묶음을 선택하세요");
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
        boolean separator = req.separatorPage() == null || req.separatorPage();

        // 장비 접근 검증(selfAndChildren) + 허용 조종원 집합(priority 순). 타사/없는 장비는 여기서 거부/제외.
        List<Long> equipmentIds = bundles.stream().map(ReviewBundlePdfRequest.Bundle::equipmentId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, List<Long>> allowedOps = equipmentService.defaultOperatorsByEquipmentIds(equipmentIds, actor);

        // DocumentType.sort_order 정렬표 — 소량 마스터라 전체 로드.
        Map<Long, Integer> sortOrder = new HashMap<>();
        for (DocumentType t : docTypes.findAll()) sortOrder.put(t.getId(), t.getSortOrder());

        // 조종원 이름 배치 로드(전 묶음 통합).
        Set<Long> allOpIds = new HashSet<>();
        for (ReviewBundlePdfRequest.Bundle b : bundles) {
            if (b.operatorPersonIds() != null) allOpIds.addAll(b.operatorPersonIds());
        }
        Map<Long, String> personName = personRepo.findAllById(allOpIds).stream()
                .collect(Collectors.toMap(Person::getId, p -> p.getName() != null ? p.getName() : ("인원" + p.getId())));

        int skippedEmpty = 0;
        List<BuiltBundle> built = new ArrayList<>();
        Long senderCompanyId = actor.companyId();

        for (ReviewBundlePdfRequest.Bundle b : bundles) {
            if (b.equipmentId() == null) continue;
            Equipment e = equipmentService.get(b.equipmentId(), actor); // 접근검증 + 라벨/공급사
            if (senderCompanyId == null) senderCompanyId = e.getSupplierId();
            String eqLabel = equipmentLabel(e);

            List<PdfMergeService.Part> parts = new ArrayList<>();
            List<EnvelopeItem> resources = new ArrayList<>();
            int bundleDocs = 0;

            // 장비 서류 (유효본만, sort_order 순)
            List<Document> eqDocs = sortedActive(OwnerType.EQUIPMENT, e.getId(), sortOrder);
            if (eqDocs.isEmpty()) {
                skippedEmpty++;
            } else {
                if (separator) parts.add(separatorPart("장비 " + eqLabel));
                for (Document d : eqDocs) addDocPart(parts, d);
                resources.add(new EnvelopeItem(OwnerType.EQUIPMENT, e.getId(), eqLabel, eqDocs.size()));
                bundleDocs += eqDocs.size();
            }

            // 조종원 서류 — 요청한 것 중 허용된 것만, priority 순
            Set<Long> requested = b.operatorPersonIds() == null ? Set.of() : new HashSet<>(b.operatorPersonIds());
            for (Long pid : allowedOps.getOrDefault(e.getId(), List.of())) {
                if (!requested.contains(pid)) continue;
                String pLabel = personName.getOrDefault(pid, "인원" + pid);
                List<Document> pDocs = sortedActive(OwnerType.PERSON, pid, sortOrder);
                if (pDocs.isEmpty()) { skippedEmpty++; continue; }
                if (separator) parts.add(separatorPart("조종원 " + pLabel));
                for (Document d : pDocs) addDocPart(parts, d);
                resources.add(new EnvelopeItem(OwnerType.PERSON, pid, pLabel, pDocs.size()));
                bundleDocs += pDocs.size();
            }

            if (bundleDocs == 0) continue; // 이 묶음은 유효 서류 전무 → 첨부 생성 안 함
            byte[] pdf = pdfMerge.merge(parts);
            // 파일명 = "차량번호_조종원이름(들)" — PDF 에 실제 포함된 조종원만.
            StringBuilder fileName = new StringBuilder(eqLabel);
            for (EnvelopeItem it : resources) {
                if (it.type() == OwnerType.PERSON) fileName.append('_').append(it.label());
            }
            built.add(new BuiltBundle(eqLabel, fileName.toString(), pdf, resources, bundleDocs));
        }

        if (built.isEmpty()) {
            throw ApiException.badRequest("NO_DOCS", "선택한 자원에 보낼 유효한 서류가 없습니다");
        }

        int totalDocs = built.stream().mapToInt(BuiltBundle::docCount).sum();

        // BP 봉투 등록(+알림) — 메일보다 먼저 저장(메일 실패 시 전체 롤백으로 중복 발송 방지).
        boolean bpDelivered = false;
        if (hasBp && senderCompanyId != null) {
            List<EnvelopeItem> all = built.stream().flatMap(x -> x.resources().stream()).toList();
            DocumentReview review = reviewRepo.save(new DocumentReview(senderCompanyId, req.bpCompanyId(),
                    req.message() != null && !req.message().isBlank() ? req.message().trim() : null, actor.id()));
            List<DocumentReviewItem> items = new ArrayList<>();
            for (EnvelopeItem it : all) {
                items.add(new DocumentReviewItem(review.getId(), it.type(), it.ownerId(), it.label(), it.docCount()));
            }
            reviewItemRepo.saveAll(items);
            notifications.sendToCompany(req.bpCompanyId(), "DOCUMENT_REVIEW", "서류 심사 도착",
                    "장비 묶음 " + built.size() + "건 / 서류 " + totalDocs + "건",
                    "DOCUMENT_REVIEW", review.getId(), null, notifications.senderLabelOf(actor));
            bpDelivered = true;
        } else if (hasBp) {
            log.warn("bundle pdf — BP 봉투 등록 불가(발신 회사 미상)");
        }

        // 이메일 — 묶음마다 병합 PDF 첨부(있을 때만). BP 지정 시 그 회사 소속 사용자 이메일을 자동 CC.
        if (!emails.isEmpty()) {
            // 발송 계정 결정(본인 등록계정 or 시스템 기본). 미등록인데 기본 미설정이면 여기서 400.
            ReviewMailComposer.Prepared prep = composer.prepare(actor);
            List<String> cc = hasBp ? bpCcEmails(req.bpCompanyId(), emails) : List.of();
            // ZIP 동시 발송 — 같은 메일에 자원별 압축도 첨부(자원 중복 제거).
            List<ZipFile> zips = Boolean.TRUE.equals(req.includeZip()) ? buildZips(built) : List.of();
            sendEmail(prep, emails, cc, req.message(), built, zips, totalDocs);
        }

        return new BundlePdfResult(built.size(), emails.size(), bpDelivered, totalDocs, skippedEmpty);
    }

    /** 자원의 유효본(chain head)을 DocumentType.sort_order → id 순으로. */
    private List<Document> sortedActive(OwnerType type, Long ownerId, Map<Long, Integer> sortOrder) {
        return docs.findActiveHeadByOwner(type, ownerId).stream()
                .sorted(Comparator.comparingInt((Document d) -> sortOrder.getOrDefault(d.getDocumentTypeId(), Integer.MAX_VALUE))
                        .thenComparing(Document::getId))
                .toList();
    }

    private void addDocPart(List<PdfMergeService.Part> parts, Document d) {
        byte[] bytes = readBytes(d.getFileKey());
        if (bytes != null) parts.add(new PdfMergeService.Part(bytes, d.getContentType(), d.getFileName()));
    }

    private byte[] readBytes(String key) {
        try {
            return storage.load(key).getInputStream().readAllBytes();
        } catch (Exception e) {
            log.warn("bundle pdf — 파일 읽기 실패 {}: {}", key, e.getMessage());
            return null;
        }
    }

    /** 자원 구분면 Part — "장비 12가1234" / "조종원 홍길동" 한 페이지. */
    private PdfMergeService.Part separatorPart(String title) {
        return new PdfMergeService.Part(separatorPdf(title), "application/pdf", title);
    }

    private byte[] separatorPdf(String title) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdf, PageSize.A4)) {
            doc.setFont(koreanFont());
            doc.add(new Paragraph(title).setFontSize(28)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(280));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("구분면 PDF 생성 실패: " + e.getMessage());
        }
    }

    private PdfFont koreanFont() throws java.io.IOException {
        if (fontBytes == null) {
            try (var in = new ClassPathResource("fonts/NanumGothicBold.ttf").getInputStream()) {
                fontBytes = in.readAllBytes();
            }
        }
        return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
    }

    private String equipmentLabel(Equipment e) {
        if (e.getVehicleNo() != null && !e.getVehicleNo().isBlank()) return e.getVehicleNo();
        if (e.getModel() != null && !e.getModel().isBlank()) return e.getModel();
        return "장비" + e.getId();
    }

    /** BP 회사 소속 사용자 이메일 — 병합 PDF 메일 자동 CC 대상. to 에 이미 있는 주소는 제외. */
    private List<String> bpCcEmails(Long bpCompanyId, List<String> to) {
        return userRepo.findByCompanyIdOrderByIdAsc(bpCompanyId).stream()
                .map(User::getEmail)
                .filter(email -> email != null && isSafeHeader(email.trim()))
                .map(String::trim)
                .filter(email -> !to.contains(email))
                .distinct()
                .toList();
    }

    /** includeZip 첨부용 — 묶음의 자원별 zip(여러 묶음에 겹치는 자원은 1회만). 서류 없는 자원은 zip null 로 제외. */
    private List<ZipFile> buildZips(List<BuiltBundle> built) {
        List<ZipFile> zips = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BuiltBundle x : built) {
            for (EnvelopeItem it : x.resources()) {
                if (!seen.add(it.type() + ":" + it.ownerId())) continue;
                DocumentZipService.ZipResult zip = zipService.zipSingleResource(it.type(), it.ownerId());
                if (zip != null) zips.add(new ZipFile(safeFileName(it.label()) + ".zip", zip.bytes()));
            }
        }
        return zips;
    }

    private void sendEmail(ReviewMailComposer.Prepared prep, List<String> emails, List<String> cc, String message,
                           List<BuiltBundle> built, List<ZipFile> zips, int totalDocs) {
        JavaMailSender mailSender = prep.sender();
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            composer.applyFrom(helper, prep); // From(표시명)·Reply-To — 등록계정 or 시스템 기본 분기
            helper.setTo(emails.toArray(new String[0]));
            if (cc != null && !cc.isEmpty()) helper.setCc(cc.toArray(new String[0]));
            String subjectCompany = prep.companyName() != null ? prep.companyName() : "서류";
            helper.setSubject("[" + subjectCompany + "] 서류 검토 요청 — 장비 " + built.size() + "건 / 서류 " + totalDocs + "건");
            List<ReviewMailComposer.Line> lines = built.stream()
                    .map(x -> new ReviewMailComposer.Line(x.label(), x.docCount())).toList();
            String note = "장비별 병합 PDF " + built.size() + "개"
                    + (zips.isEmpty() ? "" : " 및 자원별 압축(ZIP) " + zips.size() + "개");
            helper.setText(composer.renderHtml(prep, message, lines, totalDocs, note), true);
            for (BuiltBundle x : built) {
                helper.addAttachment(safeFileName(x.fileName()) + ".pdf", new ByteArrayResource(x.pdf()));
            }
            for (ZipFile z : zips) {
                helper.addAttachment(z.name(), new ByteArrayResource(z.bytes()));
            }
            mailSender.send(msg);
        } catch (MailAuthenticationException e) {
            // 본인 계정 발송인데 인증 실패 — 조용한 기본폴백 없이 명확히 알린다(발송자 의도 존중).
            log.warn("bundle pdf mail auth failed (registered={})", prep.registered());
            throw ApiException.badRequest("MAIL_AUTH_FAIL", prep.registered()
                    ? "발송 메일 인증 실패 — 앱 비밀번호를 확인하세요"
                    : "메일 서버 인증에 실패했습니다");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("bundle pdf mail send failed: {}", e.getMessage());
            throw ApiException.badRequest("MAIL_FAIL", "메일 발송에 실패했습니다");
        }
    }

    private boolean isSafeHeader(String email) {
        return SafeText.isSafeEmail(email);
    }

    private String safeFileName(String s) {
        return SafeText.sanitizeFileName(s);
    }
}
