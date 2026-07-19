package com.skep.outgoing;

import com.skep.common.ApiException;
import com.skep.common.SafeText;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.outgoing.dto.CreateOutgoingRequest;
import com.skep.outgoing.dto.OutgoingResponse;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 공급사 → BP 영업 견적 발송. PDF 첨부 + HTML 본문 + (BP 가입자) 시스템 알림.
 */
@Service
@Transactional
public class OutgoingQuotationService {

    private static final Logger log = LoggerFactory.getLogger(OutgoingQuotationService.class);

    private final OutgoingQuotationRepository repo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final NotificationService notifications;
    private final com.skep.worksheet.WorksheetMailService pdfConverter;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromAddress;

    public OutgoingQuotationService(OutgoingQuotationRepository repo,
                                     EquipmentRepository equipmentRepo,
                                     PersonRepository personRepo,
                                     CompanyRepository companyRepo,
                                     UserRepository userRepo,
                                     NotificationService notifications,
                                     com.skep.worksheet.WorksheetMailService pdfConverter,
                                     JavaMailSender mailSender) {
        this.repo = repo;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.notifications = notifications;
        this.pdfConverter = pdfConverter;
        this.mailSender = mailSender;
    }

    public OutgoingResponse send(CreateOutgoingRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사/ADMIN 만 견적 발송 가능합니다");
        }
        if ((req.equipmentId() == null) == (req.personId() == null)) {
            throw ApiException.badRequest("RESOURCE_REQUIRED", "장비 또는 인원 1개 지정 필수");
        }
        // 자원 소유권 검사
        Equipment eq = null; Person pp = null;
        if (req.equipmentId() != null) {
            eq = equipmentRepo.findById(req.equipmentId()).orElseThrow(() ->
                    ApiException.badRequest("EQUIPMENT_NOT_FOUND", "장비 없음"));
            if (actor.role() != Role.ADMIN && !eq.getSupplierId().equals(actor.companyId())) {
                throw ApiException.forbidden("EQUIPMENT_NOT_OWNED", "본인 회사 장비만 발송 가능");
            }
        } else {
            pp = personRepo.findById(req.personId()).orElseThrow(() ->
                    ApiException.badRequest("PERSON_NOT_FOUND", "인원 없음"));
            if (actor.role() != Role.ADMIN && !pp.getSupplierId().equals(actor.companyId())) {
                throw ApiException.forbidden("PERSON_NOT_OWNED", "본인 회사 인원만 발송 가능");
            }
        }

        OutgoingQuotation.RecipientType rt;
        Long recipientUserId = null, recipientCompanyId = null;
        String recipientEmail = null, recipientName = null;
        if ("REGISTERED_BP".equals(req.mode())) {
            if (req.recipientUserId() == null) {
                throw ApiException.badRequest("RECIPIENT_USER_REQUIRED", "수신 BP 사용자 필수");
            }
            User u = userRepo.findById(req.recipientUserId()).orElseThrow(() ->
                    ApiException.badRequest("USER_NOT_FOUND", "수신 사용자 없음"));
            rt = OutgoingQuotation.RecipientType.REGISTERED_BP;
            recipientUserId = u.getId();
            recipientCompanyId = u.getCompanyId();
            recipientEmail = u.getEmail();
            recipientName = u.getName();
        } else if ("EMAIL".equals(req.mode())) {
            if (req.recipientEmail() == null || req.recipientEmail().isBlank()) {
                throw ApiException.badRequest("EMAIL_REQUIRED", "수신 이메일 필수");
            }
            rt = OutgoingQuotation.RecipientType.EMAIL;
            recipientEmail = req.recipientEmail().trim();
            recipientName = "(외부)";
        } else {
            throw ApiException.badRequest("INVALID_MODE", "mode 는 REGISTERED_BP 또는 EMAIL");
        }

        OutgoingQuotation row = OutgoingQuotation.builder()
                .supplierCompanyId(actor.companyId() != null ? actor.companyId()
                        : (eq != null ? eq.getSupplierId() : pp.getSupplierId()))
                .sentByUserId(actor.id())
                .equipmentId(req.equipmentId())
                .personId(req.personId())
                .dailyRate(req.dailyRate())
                .monthlyRate(req.monthlyRate())
                .note(req.note())
                .periodStart(req.periodStart())
                .periodEnd(req.periodEnd())
                .recipientType(rt)
                .recipientUserId(recipientUserId)
                .recipientCompanyId(recipientCompanyId)
                .recipientEmail(recipientEmail)
                .build();
        repo.save(row);

        // PDF 생성은 항상 (수신함에서 다운로드용)
        byte[] pdf = null;
        try {
            String html = renderHtml(row, eq, pp);
            pdf = pdfConverter.convertHtmlToPdf(html.getBytes(StandardCharsets.UTF_8),
                    "outgoing-quotation-" + row.getId());
        } catch (Exception e) {
            log.error("Outgoing quotation PDF failed for id={}", row.getId(), e);
        }

        if (rt == OutgoingQuotation.RecipientType.REGISTERED_BP && recipientUserId != null) {
            // 등록 BP — 시스템 알림만. 이메일은 X (수신함에서 확인).
            notifications.sendToUser(recipientUserId,
                    NotificationType.QUOTATION_RECEIVED,
                    "공급사 영업 견적 수신",
                    "공급사 견적서가 도착했습니다. 수신함에서 확인 가능합니다.",
                    "OUTGOING_QUOTATION", row.getId(), null, notifications.senderLabelOf(actor));
            row.markMailResult(true, null, pdf != null ? pdf.length : null);
        } else {
            // EMAIL 모드 — 외부 이메일 발송
            try {
                if (pdf == null) throw new IllegalStateException("PDF 생성 실패");
                sendMail(recipientEmail, recipientName, row, pdf, req.ccEmails());
                row.markMailResult(true, null, pdf.length);
            } catch (Exception e) {
                log.error("Outgoing quotation mail failed for id={}", row.getId(), e);
                row.markMailResult(false, e.getMessage(), pdf != null ? pdf.length : null);
            }
        }

        return toResponse(row);
    }

    /** V37: BP 가 견적서를 수락하면서 사인. recipientUserId 본인 또는 같은 회사 BP. */
    public OutgoingResponse signByBp(Long id, String pngBase64, String signerName,
                                       AuthenticatedUser actor) {
        OutgoingQuotation o = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("OUTGOING_NOT_FOUND", "견적서 " + id + " 없음"));
        // 권한: REGISTERED_BP 이고, 수신 회사 == actor 회사 (또는 ADMIN)
        boolean isAdmin = actor.role() == com.skep.user.Role.ADMIN;
        boolean isRecipientCompany = o.getRecipientCompanyId() != null
                && o.getRecipientCompanyId().equals(actor.companyId())
                && actor.role() == com.skep.user.Role.BP;
        if (!isAdmin && !isRecipientCompany) {
            throw ApiException.forbidden("SIGN_DENIED", "수신 BP 회사만 사인 가능합니다");
        }
        if (pngBase64 == null || pngBase64.isBlank()) {
            throw ApiException.badRequest("PNG_REQUIRED", "사인 PNG 필수");
        }
        String b64 = pngBase64.startsWith("data:")
                ? pngBase64.substring(pngBase64.indexOf(',') + 1) : pngBase64;
        byte[] png;
        try { png = java.util.Base64.getDecoder().decode(b64); }
        catch (IllegalArgumentException e) {
            throw ApiException.badRequest("PNG_INVALID", "사인 PNG 디코딩 실패");
        }
        String name = (signerName == null || signerName.isBlank())
                ? userRepo.findById(actor.id()).map(User::getName).orElse(null) : signerName.trim();
        o.applyBpSignature(png, actor.id(), name);

        // Audit-Fix #2: BP 사인 → 공급사 알림
        notifications.sendToCompany(o.getSupplierCompanyId(),
                NotificationType.QUOTATION_FINALIZED,
                "BP 견적 수락",
                "발송한 견적서 #" + o.getId() + " 가 BP(" + (name != null ? name : "수신자") + ")에 의해 수락되었습니다.",
                "OUTGOING_QUOTATION", o.getId(), null);
        return toResponse(o);
    }

    @Transactional(readOnly = true)
    public byte[] getBpSignaturePng(Long id, AuthenticatedUser actor) {
        OutgoingQuotation o = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("OUTGOING_NOT_FOUND", "견적서 없음"));
        // 발송 공급사 또는 수신 BP/ADMIN
        boolean allowed = actor.role() == com.skep.user.Role.ADMIN
                || (actor.companyId() != null && actor.companyId().equals(o.getSupplierCompanyId()))
                || (actor.companyId() != null && actor.companyId().equals(o.getRecipientCompanyId()))
                || (actor.id() != null && actor.id().equals(o.getRecipientUserId()));
        if (!allowed) throw ApiException.forbidden("SIGN_VIEW_DENIED", "사인 조회 권한 없음");
        return o.getBpSignaturePng();
    }

    /** 단건 조회 — 발송 공급사 / 수신 BP / 수신자 본인 / ADMIN. */
    @Transactional(readOnly = true)
    public OutgoingResponse get(Long id, AuthenticatedUser actor) {
        OutgoingQuotation o = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("OUTGOING_NOT_FOUND", "견적서 " + id + " 없음"));
        boolean allowed = actor.role() == com.skep.user.Role.ADMIN
                || (actor.companyId() != null && actor.companyId().equals(o.getSupplierCompanyId()))
                || (actor.companyId() != null && actor.companyId().equals(o.getRecipientCompanyId()))
                || (actor.id() != null && actor.id().equals(o.getRecipientUserId()));
        if (!allowed) throw ApiException.forbidden("NOT_PERMITTED", "조회 권한 없음");
        return toResponse(o);
    }

    @Transactional(readOnly = true)
    public List<OutgoingResponse> listSent(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return repo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OutgoingResponse> listInbox(AuthenticatedUser actor) {
        if (actor.id() == null) return List.of();
        // 사용자 직접 수신 + 같은 회사 수신 둘 다 노출
        var byUser = repo.findByRecipientUserIdOrderByIdDesc(actor.id());
        var byCompany = actor.companyId() != null
                ? repo.findByRecipientCompanyIdOrderByIdDesc(actor.companyId()) : List.<OutgoingQuotation>of();
        java.util.Map<Long, OutgoingQuotation> merged = new java.util.LinkedHashMap<>();
        for (var o : byUser) merged.put(o.getId(), o);
        for (var o : byCompany) merged.putIfAbsent(o.getId(), o);
        return merged.values().stream().map(this::toResponse).toList();
    }

    // ── helpers ────────────────────────────────────────────

    private void sendMail(String to, String toName, OutgoingQuotation o, byte[] pdf,
                          List<String> ccEmails) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        // 참조(CC) — 헤더 인젝션 방어로 안전한 이메일만, 수신자 본인은 제외.
        if (ccEmails != null && !ccEmails.isEmpty()) {
            String[] safeCc = ccEmails.stream()
                    .filter(SafeText::isSafeEmail)
                    .map(String::trim)
                    .filter(e -> !e.equalsIgnoreCase(to))
                    .distinct()
                    .toArray(String[]::new);
            if (safeCc.length > 0) helper.setCc(safeCc);
        }
        helper.setSubject("[견적서] " + (o.getEquipmentId() != null ? "장비" : "인원") + " 견적 도착");
        helper.setText(textBody(o, toName), false);
        helper.addAttachment("quotation-" + o.getId() + ".pdf", new ByteArrayResource(pdf));
        mailSender.send(msg);
    }

    private String textBody(OutgoingQuotation o, String toName) {
        StringBuilder sb = new StringBuilder();
        sb.append(toName != null ? toName + " 님" : "안녕하세요").append(",\n\n");
        sb.append("공급사 견적서가 도착했습니다. 첨부 PDF 를 확인해주세요.\n\n");
        if (o.getDailyRate() != null) sb.append("일대 단가: ").append(o.getDailyRate()).append("원\n");
        if (o.getMonthlyRate() != null) sb.append("월대 단가: ").append(o.getMonthlyRate()).append("원\n");
        if (o.getPeriodStart() != null) sb.append("가능 기간: ").append(o.getPeriodStart());
        if (o.getPeriodEnd() != null) sb.append(" ~ ").append(o.getPeriodEnd());
        sb.append("\n");
        if (o.getNote() != null && !o.getNote().isBlank()) sb.append("\n메모: ").append(o.getNote()).append("\n");
        return sb.toString();
    }

    private String renderHtml(OutgoingQuotation o, Equipment eq, Person pp) {
        Company supplier = companyRepo.findById(o.getSupplierCompanyId()).orElse(null);
        String supplierName = supplier != null ? supplier.getName() : "(공급사)";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>견적서</title>")
                .append("<style>")
                .append("@page{size:A4;margin:25mm 20mm}")
                .append("body{font-family:'Korean',sans-serif;margin:0;font-size:12pt;color:#222;line-height:1.5}")
                .append("h1{text-align:center;font-size:28pt;letter-spacing:8pt;margin:0 0 20pt 0;font-weight:700}")
                .append(".meta{text-align:right;font-size:10pt;color:#666;margin-bottom:14pt}")
                .append(".supplier{margin-bottom:18pt}")
                .append(".supplier .label{font-size:9pt;color:#888}")
                .append(".supplier .name{font-size:15pt;font-weight:700;margin-top:2pt}")
                .append("table{width:100%;border-collapse:collapse;margin-bottom:16pt}")
                .append("td{border:1px solid #888;padding:9pt 12pt;vertical-align:top}")
                .append("td.k{background:#f0f0f0;width:30%;font-weight:700;color:#333}")
                .append("td.v{font-size:12pt}")
                .append("td.v.amount{font-weight:700;color:#1a4f9c;font-size:13pt}")
                .append(".foot{margin-top:30pt;text-align:center;font-size:11pt;color:#444;line-height:1.7}")
                .append(".foot .stamp{margin-top:14pt;font-size:14pt;font-weight:700}")
                .append("</style>")
                .append("</head><body>")
                .append("<h1>견  적  서</h1>")
                .append("<div class='meta'>발송일: ").append(formatDateTime(o.getSentAt())).append("<br/>")
                .append("견적서 번호: OQ-").append(o.getId()).append("</div>")
                .append("<div class='supplier'>")
                .append("<div class='label'>공급사</div>")
                .append("<div class='name'>").append(esc(supplierName)).append("</div>")
                .append("</div>")
                .append("<table>");
        if (eq != null) {
            String catLabel = categoryLabel(eq.getCategory());
            String head = nonBlank(eq.getVehicleNo(), eq.getModel(), "장비 #" + eq.getId());
            String detail = catLabel
                    + (eq.getModel() != null ? " · " + esc(eq.getModel()) : "")
                    + (eq.getVehicleNo() != null ? " · " + esc(eq.getVehicleNo()) : "");
            sb.append("<tr><td class='k'>품목</td><td class='v'><b>").append(esc(head))
                    .append("</b><br/><span style='color:#666;font-size:11pt'>").append(detail).append("</span></td></tr>");
        } else if (pp != null) {
            sb.append("<tr><td class='k'>품목</td><td class='v'><b>").append(esc(pp.getName())).append("</b> (인원)</td></tr>");
        }
        if (o.getDailyRate() != null) {
            sb.append("<tr><td class='k'>일대 단가</td><td class='v amount'>")
                    .append(fmt(o.getDailyRate())).append(" 원")
                    .append(" <span style='font-weight:400;color:#666;font-size:10pt'>(")
                    .append(toKoreanAmount(o.getDailyRate())).append(")</span></td></tr>");
        }
        if (o.getMonthlyRate() != null) {
            sb.append("<tr><td class='k'>월대 단가</td><td class='v amount'>")
                    .append(fmt(o.getMonthlyRate())).append(" 원")
                    .append(" <span style='font-weight:400;color:#666;font-size:10pt'>(")
                    .append(toKoreanAmount(o.getMonthlyRate())).append(")</span></td></tr>");
        }
        if (o.getPeriodStart() != null || o.getPeriodEnd() != null) {
            sb.append("<tr><td class='k'>가용 기간</td><td class='v'>")
                    .append(o.getPeriodStart() != null ? o.getPeriodStart() : "")
                    .append(" ~ ")
                    .append(o.getPeriodEnd() != null ? o.getPeriodEnd() : "")
                    .append("</td></tr>");
        }
        if (o.getNote() != null && !o.getNote().isBlank()) {
            sb.append("<tr><td class='k'>비고</td><td class='v'>").append(esc(o.getNote()).replace("\n", "<br/>")).append("</td></tr>");
        }
        sb.append("</table>")
                .append("<div class='foot'>")
                .append("위 견적 내용으로 제안 드립니다.<br/>")
                .append("기재된 단가는 부가세 별도이며, 세부 계약 조건은 협의 가능합니다.")
                .append("<div class='stamp'>").append(esc(supplierName)).append(" &nbsp;(인)</div>")
                .append("</div>")
                .append("</body></html>");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String fmt(Number n) {
        if (n == null) return "";
        return java.text.NumberFormat.getInstance(java.util.Locale.KOREA).format(n);
    }

    private static String formatDateTime(java.time.LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static String nonBlank(String... ss) {
        for (String s : ss) if (s != null && !s.isBlank()) return s;
        return "";
    }

    private static String toKoreanAmount(long n) {
        if (n <= 0) return "";
        long[] bases = {1_0000_0000_0000L, 1_0000_0000L, 1_0000L};
        String[] units = {"조", "억", "만"};
        StringBuilder out = new StringBuilder();
        long remain = n;
        for (int i = 0; i < bases.length; i++) {
            if (remain >= bases[i]) {
                long q = remain / bases[i];
                if (out.length() > 0) out.append(' ');
                out.append(fmt(q)).append(units[i]);
                remain %= bases[i];
            }
        }
        if (remain > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(fmt(remain));
        }
        out.append("원");
        return out.toString();
    }

    private static String categoryLabel(String cat) {
        if (cat == null) return "";
        return switch (cat) {
            case "AERIAL_LIFT" -> "고소작업대";
            case "CRANE" -> "크레인";
            case "EXCAVATOR" -> "굴착기";
            case "BULLDOZER" -> "불도저";
            case "FORKLIFT" -> "지게차";
            case "LOADER" -> "로더";
            case "PUMP_TRUCK" -> "펌프카";
            case "DUMP_TRUCK" -> "덤프트럭";
            case "CONCRETE_MIXER" -> "콘크리트 믹서";
            case "ROAD_ROLLER" -> "로드롤러";
            case "SCISSOR_LIFT" -> "시저리프트";
            default -> cat;
        };
    }

    private OutgoingResponse toResponse(OutgoingQuotation o) {
        Company supplier = companyRepo.findById(o.getSupplierCompanyId()).orElse(null);
        Company recipientCo = o.getRecipientCompanyId() != null
                ? companyRepo.findById(o.getRecipientCompanyId()).orElse(null) : null;
        String eqLabel = null, ppLabel = null;
        if (o.getEquipmentId() != null) {
            equipmentRepo.findById(o.getEquipmentId()).ifPresent(e ->
                    log.debug("eq found"));
            Equipment e = equipmentRepo.findById(o.getEquipmentId()).orElse(null);
            if (e != null) eqLabel = e.getVehicleNo() != null ? e.getVehicleNo()
                    : (e.getModel() != null ? e.getModel() : "장비#" + e.getId());
        }
        if (o.getPersonId() != null) {
            Person p = personRepo.findById(o.getPersonId()).orElse(null);
            if (p != null) ppLabel = p.getName();
        }
        return OutgoingResponse.from(o,
                supplier != null ? supplier.getName() : null,
                eqLabel, ppLabel,
                recipientCo != null ? recipientCo.getName() : null);
    }
}
