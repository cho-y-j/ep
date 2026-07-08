package com.skep.alimtalk;

import com.skep.sms.SmsLog;
import com.skep.sms.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 다온톡 알림톡 발송 — 템플릿 #{변수} 치환 → 알림톡 → (실패 시) SMS 대체 → sms_logs 기록.
 * sms_logs/SmsLog 재사용(새 테이블 없음). 발송 실패는 호출자 로직에 영향 X(swallow).
 *
 * H-7: 수신자별 동기 HTTP(최대 15s)가 요청 트랜잭션 안에서 루프 돌면 스레드+DB커넥션을 수십초 점유.
 * → send 를 @Async 로 전환. 흐름 호출자(견적/점검 생성)는 반환 future 를 무시하면 즉시 논블로킹 진행.
 * 별도 워커 스레드라 호출자 트랜잭션과 분리되며, 단건 sms_logs 저장은 repository 트랜잭션으로 독립 커밋.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlimTalkService {

    private static final Pattern VAR = Pattern.compile("#\\{([^}]+)\\}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DaonAlimTalkClient client;
    private final SmsLogRepository logs;

    @Value("${skep.alimtalk.brand-name:SKEP}")
    private String brandName;

    @Value("${skep.alimtalk.sms-callback:010-9780-4669}")
    private String smsCallback;

    /** 발송 결과 (독립 발송 화면용. 흐름 호출자는 반환값 무시 가능). */
    public record SendResult(String phone, String status, String provider, String message) {}

    /**
     * 브랜드명/요청일시는 자동 주입(호출자가 vars 에 넣으면 그 값 우선).
     * @Async — 별도 워커 스레드에서 발송. 흐름 호출자는 반환 future 를 무시(논블로킹).
     * 동기 결과가 필요한 직접 발송 화면은 future.join() 으로 대기(AlimTalkController).
     */
    @Async
    public CompletableFuture<SendResult> send(String phone, AlimTalkTemplate template, Map<String, String> vars, Long sentBy) {
        if (phone == null || phone.isBlank()) {
            return CompletableFuture.completedFuture(new SendResult(phone, "SKIPPED", null, "빈 번호"));
        }

        Map<String, String> v = new HashMap<>(vars);
        v.putIfAbsent("브랜드명", brandName);
        v.putIfAbsent("요청일시", ZonedDateTime.now(KST).format(STAMP));
        String content = fill(template.content, v);
        String userKey = nextUserKey();

        try {
            var r = client.sendAlimtalk(userKey, phone, template.code, content);
            if (r.success()) {
                logSave(phone, content, template.code, "SENT", "DAON_ALIMTALK", r.sendCode(), null, sentBy);
                return CompletableFuture.completedFuture(new SendResult(phone, "SENT", "DAON_ALIMTALK", null));
            }
            // 알림톡 실패 → SMS 대체발송
            var sms = client.sendSms(userKey, phone, smsCallback, content);
            String status = sms.success() ? "SENT" : "FAILED";
            String detail = sms.success() ? null : abbr(r.message() + " / sms:" + sms.message());
            logSave(phone, content, template.code, status, "DAON_SMS", sms.sendCode(), detail, sentBy);
            return CompletableFuture.completedFuture(new SendResult(phone, status, "DAON_SMS", detail));
        } catch (Exception e) {
            log.warn("alimtalk send error: {}", e.getMessage());
            logSave(phone, content, template.code, "FAILED", "DAON_ALIMTALK", null, abbr(e.getMessage()), sentBy);
            return CompletableFuture.completedFuture(new SendResult(phone, "FAILED", "DAON_ALIMTALK", abbr(e.getMessage())));
        }
    }

    /** 자유 텍스트 SMS 발송 (알림톡 템플릿 없이) — 서류 수집 링크 등. 동기 반환. */
    public SendResult sendSmsText(String phone, String content, Long sentBy) {
        if (phone == null || phone.isBlank()) return new SendResult(phone, "SKIPPED", null, "빈 번호");
        String userKey = nextUserKey();
        try {
            var sms = client.sendSms(userKey, phone, smsCallback, content);
            String status = sms.success() ? "SENT" : "FAILED";
            String detail = sms.success() ? null : abbr(sms.message());
            logSave(phone, content, "SMS_TEXT", status, "DAON_SMS", sms.sendCode(), detail, sentBy);
            return new SendResult(phone, status, "DAON_SMS", detail);
        } catch (Exception e) {
            log.warn("sms text send error: {}", e.getMessage());
            logSave(phone, content, "SMS_TEXT", "FAILED", "DAON_SMS", null, abbr(e.getMessage()), sentBy);
            return new SendResult(phone, "FAILED", "DAON_SMS", abbr(e.getMessage()));
        }
    }

    static String fill(String content, Map<String, String> vars) {
        Matcher m = VAR.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void logSave(String phone, String msg, String purpose, String status,
                         String provider, String externalId, String err, Long sentBy) {
        try {
            logs.save(SmsLog.builder()
                    .phone(phone).message(msg).purpose(purpose).status(status)
                    .provider(provider).externalId(externalId).errorMessage(err).sentBy(sentBy).build());
        } catch (Exception ignored) {}
    }

    private static String abbr(String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    /** 게이트웨이 추적용 userKey(메시지별 고유). nanoTime base36 — 호출마다 상이. */
    private static String nextUserKey() {
        return "a" + Long.toString(System.nanoTime() & 0x7FFFFFFFFFFFL, 36);
    }
}
