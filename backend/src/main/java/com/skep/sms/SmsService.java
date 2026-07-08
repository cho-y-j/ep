package com.skep.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SMS 발송 인프라.
 * SKEP_SMS_ENABLED=false (default) → log 만 + sms_logs status='DISABLED'.
 * SKEP_SMS_ENABLED=true → WideShot HTTP 호출 (TODO — 키 들어오면 구현).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsService {

    private final SmsLogRepository logs;

    @Value("${skep.sms.enabled:false}")
    private boolean enabled;

    @Value("${skep.sms.sender:}")
    private String sender;

    /**
     * 단건 SMS 발송. 실패해도 호출자 로직 영향 X (별도 트랜잭션, 예외 swallow).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(String phone, String message, String purpose, Long sentBy) {
        if (phone == null || phone.isBlank()) return;
        if (message == null || message.isBlank()) return;

        if (!enabled) {
            // disabled — log 만
            log.info("[SMS:DISABLED] to={} purpose={} msg={}", mask(phone), purpose, abbr(message));
            try {
                logs.save(SmsLog.builder()
                        .phone(phone).message(message).purpose(purpose)
                        .status("DISABLED").sentBy(sentBy).build());
            } catch (Exception ignored) {}
            return;
        }

        // TODO: WideShot HTTP 호출. 키 받기 전까진 PENDING 상태로 INSERT 만.
        try {
            logs.save(SmsLog.builder()
                    .phone(phone).message(message).purpose(purpose)
                    .status("PENDING").provider("WideShot").sentBy(sentBy).build());
            log.info("[SMS:PENDING] to={} purpose={} sender={}", mask(phone), purpose, sender);
        } catch (Exception e) {
            log.warn("sms log save failed: {}", e.getMessage());
        }
    }

    private String abbr(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    /** 로그용 전화 마스킹 010-1234-****. */
    private static String mask(String phone) {
        if (phone == null) return "***";
        String d = phone.replaceAll("[^0-9]", "");
        if (d.length() < 8) return "***";
        return d.substring(0, 3) + "-" + d.substring(3, d.length() - 4) + "-****";
    }
}
