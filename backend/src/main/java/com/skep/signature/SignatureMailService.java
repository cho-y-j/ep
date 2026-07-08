package com.skep.signature;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 전자서명 요청 이메일 발송 (Naver SMTP).
 *
 * skep `WorksheetMailService` 패턴 그대로 — Naver SMTP는 From이 반드시 계정 본인이어야 함 (554 unauthorized).
 * 사용자가 입력한 from은 Reply-To 로 사용, 실제 발신자는 defaultFrom 으로 강제.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureMailService {

    private final JavaMailSender mailSender;

    @Value("${MAIL_USERNAME:}")
    private String defaultFrom;

    @Value("${SKEP_PUBLIC_BASE_URL:http://localhost:8082}")
    private String publicBaseUrl;

    /**
     * 사인 요청 메일 발송.
     *
     * @param toEmail        수신자 이메일
     * @param recipientName  수신자 이름 (메일 본문 인사)
     * @param roleLabel      역할 한글명 (담당자/확인자/검토자/승인자)
     * @param workPlanTitle  작업계획서 제목
     * @param signToken      사인 토큰 (URL에 포함)
     * @param requesterName  요청한 사람 (BP 본인) 이름
     */
    public void sendSignRequest(String toEmail, String recipientName, String roleLabel,
                                 String workPlanTitle, String signToken, String requesterName) {
        sendSignRequest(toEmail, recipientName, roleLabel, workPlanTitle, signToken, requesterName, null, null);
    }

    /** PDF 첨부 옵션. pdfBytes 가 null 이면 첨부 없이 텍스트 메일만 발송. */
    public void sendSignRequest(String toEmail, String recipientName, String roleLabel,
                                 String workPlanTitle, String signToken, String requesterName,
                                 byte[] pdfBytes, String pdfFilename) {
        if (defaultFrom == null || defaultFrom.isBlank()) {
            throw new IllegalStateException("SMTP 발신 계정이 설정되지 않았습니다 (MAIL_USERNAME 미설정)");
        }
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("수신자 이메일이 필요합니다");
        }

        String signUrl = publicBaseUrl + "/sign/" + signToken;
        String subject = "[SKEP] 작업계획서 " + roleLabel + " 사인 요청 — " + workPlanTitle;

        StringBuilder body = new StringBuilder();
        body.append((recipientName == null || recipientName.isBlank()) ? "안녕하세요" : recipientName + "님 안녕하세요").append(",\n\n");
        body.append("아래 작업계획서에 ").append(roleLabel).append(" 사인을 요청드립니다.\n\n");
        body.append("- 작업계획서: ").append(workPlanTitle == null ? "" : workPlanTitle).append("\n");
        if (requesterName != null && !requesterName.isBlank()) {
            body.append("- 요청자: ").append(requesterName).append("\n");
        }
        if (pdfBytes != null && pdfBytes.length > 0) {
            body.append("\n첨부된 작업계획서 PDF를 검토하신 뒤 아래 링크로 사인 부탁드립니다.\n");
            body.append("(사인 페이지에서도 최신 PDF 미리보기/다운로드 가능)\n\n");
        } else {
            body.append("\n아래 링크를 클릭하시면 사인 페이지로 이동합니다 (7일간 유효).\n");
            body.append("사인 페이지에서 작업계획서 PDF 미리보기/다운로드도 가능합니다.\n\n");
        }
        body.append(signUrl).append("\n\n");
        body.append("감사합니다.\nSKEP 시스템");

        boolean withAttachment = pdfBytes != null && pdfBytes.length > 0;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, withAttachment, "UTF-8");
            helper.setFrom(defaultFrom);
            helper.setTo(toEmail.trim());
            helper.setSubject(subject);
            helper.setText(body.toString(), false);
            if (withAttachment) {
                String fname = (pdfFilename == null || pdfFilename.isBlank())
                        ? "work-plan.pdf"
                        : (pdfFilename.toLowerCase().endsWith(".pdf") ? pdfFilename : pdfFilename + ".pdf");
                helper.addAttachment(fname, new ByteArrayResource(pdfBytes));
            }
            mailSender.send(msg);
            // 토큰 전체를 로그에 남기지 않음 — 로그 파일이 누설되면 사인 페이지 무단 접근 가능.
            String tokenHint = signToken == null || signToken.length() < 8 ? "?" : signToken.substring(0, 6) + "…";
            log.info("Signature request mail sent: to={} role={} token={} pdfBytes={}",
                    toEmail, roleLabel, tokenHint, withAttachment ? pdfBytes.length : 0);
        } catch (Exception e) {
            log.error("사인 요청 메일 발송 실패: to={} role={}", toEmail, roleLabel, e);
            throw new RuntimeException("메일 발송 실패: " + e.getMessage(), e);
        }
    }
}
