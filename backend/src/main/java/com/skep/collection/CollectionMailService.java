package com.skep.collection;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** 수집된 서류를 합친 PDF를 이메일로 발송. 발신자는 defaultFrom 으로 강제(헤더 인젝션 방어). */
@Service
public class CollectionMailService {

    private static final Logger log = LoggerFactory.getLogger(CollectionMailService.class);

    private final JavaMailSender mailSender;

    @Value("${MAIL_USERNAME:}")
    private String defaultFrom;

    public CollectionMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPdf(String toEmail, String subject, String bodyText, byte[] pdfBytes, String pdfFilename) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            if (defaultFrom != null && !defaultFrom.isBlank()) helper.setFrom(defaultFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(bodyText, false);
            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment(pdfFilename, new ByteArrayResource(pdfBytes));
            }
            mailSender.send(msg);
            log.info("수집 서류 PDF 메일 발송 완료 → {}", maskEmail(toEmail));
        } catch (Exception e) {
            log.error("수집 서류 PDF 메일 발송 실패 → {} : {}", maskEmail(toEmail), e.getMessage());
            throw new IllegalStateException("이메일 발송에 실패했습니다: " + e.getMessage());
        }
    }

    private static String maskEmail(String e) {
        if (e == null || !e.contains("@")) return "***";
        int at = e.indexOf('@');
        String name = e.substring(0, at);
        String shown = name.length() <= 2 ? name.charAt(0) + "*" : name.substring(0, 2) + "***";
        return shown + e.substring(at);
    }
}
