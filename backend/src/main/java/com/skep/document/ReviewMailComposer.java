package com.skep.document;

import com.skep.common.ApiException;
import com.skep.common.MailCredentialCipher;
import com.skep.common.SafeText;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.User;
import com.skep.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Properties;

/**
 * 심사 메일(자원별 ZIP · 장비별 병합 PDF 두 경로) 공용 헬퍼.
 * - 발송 계정 결정: 발송자(actor)가 본인 메일 계정을 등록했으면 그 계정으로 동적 발송(From=등록메일),
 *   미등록이면 시스템 기본 계정 발송(보낸사람 표시명=담당자(회사) + Reply-To=본인 로그인 이메일).
 * - 사업적 정식 HTML 본문(인사·용건·자원 표·첨부 안내·웹 확인·서명) 생성.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewMailComposer {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final MailCredentialCipher cipher;
    private final ObjectProvider<JavaMailSender> defaultSenderProvider;

    /** 시스템 기본 발송 계정(인증 From). 미등록 발송 경로에서 사용. */
    @Value("${MAIL_USERNAME:}")
    private String defaultFrom;

    @Value("${spring.mail.host:smtp.naver.com}")
    private String smtpHost;

    @Value("${spring.mail.port:465}")
    private int smtpPort;

    /** 심사 메일 "웹에서 확인" 링크 base — dev=http://localhost:5185. */
    @Value("${SKEP_PUBLIC_BASE_URL:http://localhost:8082}")
    private String publicBaseUrl;

    /** 본문 자원 표의 한 줄(자원명·서류 건수). */
    public record Line(String label, int docCount) {}

    /** 발송 준비 결과 — 실제 발송자(sender) + From/서명/제목에 쓸 신원. */
    public record Prepared(
            JavaMailSender sender,
            boolean registered,   // 본인 계정 발송 여부(로그·인증실패 문구 분기)
            String fromEmail,     // From 주소
            String fromDisplay,   // From 표시명
            String replyTo,       // 미등록 시 본인 로그인 이메일(회신 경로). 등록 시 null.
            String companyName,   // 서명/제목용(없으면 null)
            String contactName,   // 담당자명
            String phone,         // 연락처(없으면 null)
            String contactEmail   // 서명 이메일(등록메일 or 로그인 이메일)
    ) {}

    /**
     * 발송 계정 결정. 등록계정이 있으면 동적 sender, 없으면 시스템 기본 sender.
     * 미등록인데 기본 sender 미설정이면 400(MAIL_DISABLED). 등록계정 복호화 실패 시 400.
     */
    public Prepared prepare(AuthenticatedUser actor) {
        User u = users.findById(actor.id()).orElse(null);
        Company company = actor.companyId() == null ? null
                : companies.findById(actor.companyId()).orElse(null);
        String companyName = company != null ? company.getName() : null;
        String contactName = actor.name() != null && !actor.name().isBlank() ? actor.name() : actor.email();
        String phone = u != null && u.getPhone() != null && !u.getPhone().isBlank() ? u.getPhone()
                : (company != null ? company.getPhone() : null);

        boolean hasRegistered = u != null && u.getMailSenderEmail() != null && !u.getMailSenderEmail().isBlank();
        if (hasRegistered) {
            String senderEmail = u.getMailSenderEmail().trim();
            String password;
            try {
                password = cipher.decrypt(u.getMailSenderPasswordEnc());
            } catch (Exception e) {
                log.warn("발송 메일 계정 복호화 실패 userId={}", actor.id());
                throw ApiException.badRequest("MAIL_CRED_DECRYPT_FAIL",
                        "발송 메일 계정 정보를 읽을 수 없습니다. 설정에서 다시 등록해 주세요");
            }
            String display = u.getMailSenderName() != null && !u.getMailSenderName().isBlank()
                    ? u.getMailSenderName().trim() : contactName;
            return new Prepared(buildSender(senderEmail, password), true, senderEmail, display, null,
                    companyName, contactName, phone, senderEmail);
        }

        JavaMailSender base = defaultSenderProvider.getIfAvailable();
        if (base == null) {
            throw ApiException.badRequest("MAIL_DISABLED", "메일 발송이 설정되지 않았습니다");
        }
        // 미등록 — 시스템 계정 From + 보낸사람 표시명(담당자 (회사명)) + 회신은 본인 로그인 이메일.
        String display = companyName != null ? contactName + " (" + companyName + ")" : contactName;
        String replyTo = actor.email() != null && SafeText.isSafeEmail(actor.email())
                && !actor.email().equalsIgnoreCase(defaultFrom) ? actor.email() : null;
        return new Prepared(base, false, defaultFrom, display, replyTo,
                companyName, contactName, phone, actor.email());
    }

    /** From(표시명 포함)·Reply-To 를 helper 에 적용. */
    public void applyFrom(MimeMessageHelper helper, Prepared p) throws Exception {
        if (p.fromEmail() != null && !p.fromEmail().isBlank()) {
            helper.setFrom(p.fromEmail(), p.fromDisplay());
        }
        if (p.replyTo() != null) {
            helper.setReplyTo(p.replyTo());
        }
    }

    /** 등록 계정용 동적 JavaMailSender — host/port/SSL 은 application.yml 준용. */
    private JavaMailSender buildSender(String username, String password) {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(smtpHost);
        impl.setPort(smtpPort);
        impl.setUsername(username);
        impl.setPassword(password);
        impl.setDefaultEncoding("UTF-8");
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        return impl;
    }

    /** 사업적 정식 HTML 본문 — 상단 발신 바 · 인사 · 용건/메모 · 자원 표 · 첨부 안내 · 웹 확인 버튼 · 서명 블록. */
    public String renderHtml(Prepared p, String message, List<Line> resources, int totalDocs, String attachmentNote) {
        String company = esc(p.companyName());
        String contact = esc(p.contactName());
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:'Malgun Gothic','Apple SD Gothic Neo',sans-serif;max-width:600px;margin:0 auto;color:#1e293b;font-size:14px;line-height:1.7\">");

        // 상단 발신 바
        sb.append("<div style=\"background:#0f172a;color:#ffffff;padding:16px 20px;border-radius:8px 8px 0 0\">");
        sb.append("<div style=\"font-size:16px;font-weight:700\">서류 검토 요청</div>");
        sb.append("<div style=\"font-size:13px;color:#cbd5e1;margin-top:4px\">발신: ")
                .append(company.isEmpty() ? contact : company + " · " + contact).append("</div>");
        sb.append("</div>");

        // 본문 박스
        sb.append("<div style=\"border:1px solid #e2e8f0;border-top:none;padding:22px 20px;border-radius:0 0 8px 8px\">");

        // 인사
        sb.append("<p style=\"margin:0 0 14px\">안녕하십니까.");
        sb.append(company.isEmpty() ? " " + contact + "입니다." : " " + company + " " + contact + "입니다.");
        sb.append("</p>");

        // 용건 / 메모
        if (message != null && !message.isBlank()) {
            sb.append("<p style=\"margin:0 0 14px;white-space:pre-line\">").append(esc(message.trim())).append("</p>");
        } else {
            sb.append("<p style=\"margin:0 0 14px\">요청하신 서류를 아래와 같이 송부드립니다. 검토 후 회신 부탁드립니다.</p>");
        }

        // 자원 표
        sb.append("<table style=\"width:100%;border-collapse:collapse;margin:6px 0 14px;font-size:13px\">");
        sb.append("<thead><tr style=\"background:#f1f5f9\">");
        sb.append("<th style=\"text-align:left;padding:8px 10px;border:1px solid #e2e8f0\">자원</th>");
        sb.append("<th style=\"text-align:right;padding:8px 10px;border:1px solid #e2e8f0;width:90px\">서류</th>");
        sb.append("</tr></thead><tbody>");
        for (Line r : resources) {
            sb.append("<tr><td style=\"padding:8px 10px;border:1px solid #e2e8f0\">").append(esc(r.label()))
                    .append("</td><td style=\"padding:8px 10px;border:1px solid #e2e8f0;text-align:right\">")
                    .append(r.docCount()).append("건</td></tr>");
        }
        sb.append("<tr style=\"background:#f8fafc;font-weight:700\">");
        sb.append("<td style=\"padding:8px 10px;border:1px solid #e2e8f0\">합계 (자원 ").append(resources.size()).append("건)</td>");
        sb.append("<td style=\"padding:8px 10px;border:1px solid #e2e8f0;text-align:right\">").append(totalDocs).append("건</td>");
        sb.append("</tr></tbody></table>");

        // 첨부 안내
        if (attachmentNote != null && !attachmentNote.isBlank()) {
            sb.append("<p style=\"margin:0 0 14px;font-size:13px;color:#475569\">첨부: ").append(esc(attachmentNote)).append("</p>");
        }

        // 웹 확인 버튼(가입 담당자용)
        String url = publicBaseUrl + "/document-reviews/received";
        sb.append("<div style=\"margin:18px 0\"><a href=\"").append(esc(url))
                .append("\" style=\"display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:600;font-size:13px\">웹에서 서류 확인</a>");
        sb.append("<div style=\"font-size:12px;color:#94a3b8;margin-top:6px\">시스템 가입 담당자는 위 버튼으로 인라인 열람·승인/반려할 수 있습니다.</div></div>");

        // 서명 블록
        sb.append("<div style=\"border-top:1px solid #e2e8f0;margin-top:20px;padding-top:14px;font-size:13px;color:#475569\">");
        if (!company.isEmpty()) sb.append("<div style=\"font-weight:700;color:#1e293b\">").append(company).append("</div>");
        sb.append("<div>담당자: ").append(contact).append("</div>");
        if (p.phone() != null && !p.phone().isBlank()) sb.append("<div>연락처: ").append(esc(p.phone())).append("</div>");
        if (p.contactEmail() != null && !p.contactEmail().isBlank()) sb.append("<div>이메일: ").append(esc(p.contactEmail())).append("</div>");
        sb.append("</div>");

        sb.append("</div></div>");
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
