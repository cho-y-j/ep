package com.skep.quotation.bundle;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentService;
import com.skep.document.OwnerType;
import com.skep.notification.NotificationService;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.bundle.dto.BundleResponse;
import com.skep.quotation.bundle.dto.SendBundleRequest;
import com.skep.quotation.dispatch.DispatchedEquipment;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.quotation.pdf.QuotationPdfService;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentBundleService {

    private final DocumentBundleRepository bundles;
    private final QuotationRequestRepository requests;
    private final DispatchedEquipmentRepository dispatched;
    private final CompanyRepository companies;
    private final DocumentRepository docs;
    private final DocumentService documentService;
    private final UserRepository users;
    private final NotificationService notifications;
    private final QuotationPdfService pdfService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** 공급사가 dispatched 차량의 서류를 BP 에 명시적으로 send. 견적당 공급사 1회 멱등. */
    @Transactional
    public BundleResponse send(Long requestId, SendBundleRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "장비공급사만 가능");
        }
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));

        Long supplierCompanyId = actor.role() == Role.ADMIN
                ? null : actor.companyId();
        // 자기 회사가 차량 send 했는지 확인 (없으면 send 불가)
        List<DispatchedEquipment> myDispatched = supplierCompanyId != null
                ? dispatched.findByQuotationRequestIdAndSupplierCompanyId(requestId, supplierCompanyId)
                : dispatched.findByQuotationRequestId(requestId);
        if (myDispatched.isEmpty()) {
            throw ApiException.badRequest("NO_DISPATCH", "차량을 먼저 보내야 서류 묶음을 보낼 수 있습니다");
        }
        if (supplierCompanyId == null) supplierCompanyId = myDispatched.get(0).getSupplierCompanyId();

        if (bundles.existsByQuotationRequestIdAndSupplierCompanyId(requestId, supplierCompanyId)) {
            throw ApiException.conflict("ALREADY_SENT", "이미 서류 묶음을 보냈습니다");
        }

        DocumentBundle bundle = DocumentBundle.builder()
                .quotationRequestId(requestId)
                .supplierCompanyId(supplierCompanyId)
                .sentBy(actor.id())
                .includeEmail(req != null && req.includeEmail())
                .notes(req != null ? req.notes() : null)
                .build();
        try {
            bundles.save(bundle);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_SENT", "이미 서류 묶음을 보냈습니다");
        }

        // BP 회사 알림
        Long bpCompanyId = qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
        String supplierName = companies.findById(supplierCompanyId).map(Company::getName).orElse("공급사");
        if (bpCompanyId != null) {
            int eqCount = myDispatched.size();
            int docCount = countDocsForEquipments(myDispatched);
            notifications.sendToCompany(bpCompanyId,
                    "DOCUMENT_BUNDLE",
                    "서류 묶음 도착",
                    supplierName + " — 차량 " + eqCount + "대 / 서류 " + docCount + "건",
                    "QUOTATION_REQUEST", requestId, qr.getSiteId());
        }

        // 이메일 옵션 발송 (best-effort, 실패해도 send 자체는 성공)
        if (bundle.isIncludeEmail() && bpCompanyId != null) {
            List<String> explicit = req != null && req.emails() != null
                    ? req.emails().stream().map(String::trim).filter(this::isSafeHeader).toList()
                    : List.of();
            tryEmailToBp(bundle, qr, myDispatched, bpCompanyId, supplierName, explicit, actor);
        }

        return BundleResponse.from(bundle, supplierName);
    }

    @Transactional(readOnly = true)
    public List<BundleResponse> listByRequest(Long requestId, AuthenticatedUser actor) {
        // M-5: 공급사 시점이면 자기 회사 묶음만 — 타 공급사 서류묶음 메타 노출 방지. ADMIN/BP 는 전체.
        boolean isSupplier = actor != null
                && (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER);
        return bundles.findByQuotationRequestId(requestId).stream()
                .filter(b -> !isSupplier || (actor.companyId() != null
                        && actor.companyId().equals(b.getSupplierCompanyId())))
                .map(b -> BundleResponse.from(b,
                        companies.findById(b.getSupplierCompanyId()).map(Company::getName).orElse(null)))
                .toList();
    }

    private int countDocsForEquipments(List<DispatchedEquipment> dispatchedList) {
        int total = 0;
        for (var d : dispatchedList) {
            total += docs.findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType.EQUIPMENT, d.getEquipmentId()).size();
        }
        return total;
    }

    private void tryEmailToBp(DocumentBundle bundle, QuotationRequest qr,
                              List<DispatchedEquipment> dispatchedList, Long bpCompanyId, String supplierName,
                              List<String> explicitEmails, AuthenticatedUser actor) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("mail sender unavailable — skip email for bundle {}", bundle.getId());
            return;
        }
        // 1. 클라이언트가 명시한 이메일이 있으면 그걸 사용 (사용자가 BP UI에서 수정/추가한 결과)
        // 2. 없으면 BP 회사 관리자 이메일 자동
        List<String> recipients = !explicitEmails.isEmpty()
                ? explicitEmails
                : users.findByCompanyIdAndIsCompanyAdminTrue(bpCompanyId).stream()
                        .map(User::getEmail)
                        .filter(this::isSafeHeader)
                        .toList();
        if (recipients.isEmpty()) {
            log.warn("no safe BP admin email for company {} — skip", bpCompanyId);
            return;
        }

        try {
            byte[] pdf = pdfService.render(qr.getId(), QuotationPdfService.Mode.SINGLE, actor);
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(recipients.toArray(new String[0]));
            // supplierName 도 헤더에 들어가므로 CRLF 제거 후 사용
            String safeSupplier = stripHeaderChars(supplierName);
            helper.setSubject("[서류 묶음] " + safeSupplier + " — 견적 #" + qr.getId());
            helper.setText("[" + safeSupplier + "] 가 견적 #" + qr.getId()
                    + " 의 차량 서류 묶음을 보냈습니다.\n첨부 파일: 견적서 PDF + 차량별 서류 원본.\n\n시스템에서 확인하세요.");
            helper.addAttachment("quotation-" + qr.getId() + ".pdf", new ByteArrayResource(pdf));

            // 차량별 documents 원본 첨부
            int idx = 1;
            for (DispatchedEquipment d : dispatchedList) {
                var docList = docs.findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType.EQUIPMENT, d.getEquipmentId());
                for (Document doc : docList) {
                    try (InputStream in = documentService.loadFile(doc).getInputStream()) {
                        byte[] bytes = in.readAllBytes();
                        String rawName = Optional.ofNullable(doc.getFileName()).orElse("file");
                        // 파일명도 헤더에 들어가므로 CRLF/제어문자 제거.
                        String fname = "eq" + d.getEquipmentId() + "-" + (idx++) + "-" + stripHeaderChars(rawName);
                        helper.addAttachment(fname, new ByteArrayResource(bytes));
                    } catch (Exception ex) {
                        log.warn("attachment fail doc {} {}", doc.getId(), ex.getMessage());
                    }
                }
            }
            mailSender.send(msg);
            bundle.markEmailSent();
            bundles.save(bundle);
        } catch (Exception e) {
            log.warn("bundle email send failed for {}: {}", bundle.getId(), e.getMessage());
        }
    }

    /** 헤더에 들어갈 이메일 주소 검증 — CRLF/제어문자 / @ 누락 차단. */
    private boolean isSafeHeader(String email) {
        return com.skep.common.SafeText.isSafeEmail(email);
    }

    /** 헤더(subject/filename) 에 들어갈 사용자 입력 문자열의 CRLF/제어문자 제거. */
    private String stripHeaderChars(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\r\\n\\u0000-\\u001F]", " ").trim();
    }
}
