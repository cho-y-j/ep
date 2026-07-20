package com.skep.user;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 가입 승인 통지 메일 (Naver SMTP) — CollectionMailService/SignatureMailService 와 동일한
 * JavaMailSender + defaultFrom 패턴. 단, @Async fire-and-forget + 예외 삼킴이라
 * 발송 실패가 승인 트랜잭션(UserService.enable)에 영향을 주지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupApprovalMailService {

    private final JavaMailSender mailSender;

    /** Naver/Gmail SMTP 는 From 이 반드시 인증 계정 본인. */
    @Value("${MAIL_USERNAME:}")
    private String defaultFrom;

    @Value("${SKEP_PUBLIC_BASE_URL:http://localhost:8082}")
    private String publicBaseUrl;

    @Async
    public void sendApprovalMail(String toEmail, String recipientName) {
        if (toEmail == null || toEmail.isBlank()) return;
        if (defaultFrom == null || defaultFrom.isBlank()) {
            log.warn("가입 승인 메일 생략 — MAIL_USERNAME 미설정");
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(defaultFrom);
            helper.setTo(toEmail.trim());
            helper.setSubject("[SKEP] 가입이 승인되었습니다");
            StringBuilder body = new StringBuilder();
            body.append((recipientName == null || recipientName.isBlank())
                    ? "안녕하세요" : recipientName + "님 안녕하세요").append(",\n\n");
            body.append("SKEP 가입 신청이 승인되었습니다. 이제 로그인하실 수 있습니다.\n\n");
            body.append(publicBaseUrl).append("\n\n");
            body.append("감사합니다.\nSKEP 시스템");
            helper.setText(body.toString(), false);
            mailSender.send(msg);
            log.info("가입 승인 메일 발송 완료 → {}", toEmail);
        } catch (Exception e) {
            log.warn("가입 승인 메일 발송 실패 → {} : {}", toEmail, e.getMessage());
        }
    }
}
