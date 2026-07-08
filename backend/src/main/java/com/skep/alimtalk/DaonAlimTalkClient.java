package com.skep.alimtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 다온톡(와이드샷/세종) 알림톡 + SMS 발송 클라이언트. retalk WideShotClient 이식(self-contained).
 * 키 미설정 시 isReady()=false → 발송 skip(로그만). 운영 .env 로 활성화.
 */
@Slf4j
@Component
public class DaonAlimTalkClient {

    private final String baseUrl;
    private final String apiKey;
    private final String senderKey;
    private final boolean enabled;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DaonAlimTalkClient(
            @Value("${skep.alimtalk.base-url:https://apimsg-test.wideshot.co.kr}") String baseUrl,
            @Value("${skep.alimtalk.api-key:}") String apiKey,
            @Value("${skep.alimtalk.sender-key:}") String senderKey,
            @Value("${skep.alimtalk.enabled:false}") boolean enabled,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.senderKey = senderKey;
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(f);
    }

    public boolean isReady() {
        return enabled && apiKey != null && !apiKey.isBlank() && senderKey != null && !senderKey.isBlank();
    }

    public record Result(boolean success, String code, String sendCode, String message) {}

    /** 알림톡 발송. contents 는 변수 치환 완료된 본문. */
    public Result sendAlimtalk(String userKey, String receiverTelNo, String templateCode, String contents) {
        if (!isReady()) {
            log.info("[ALIMTALK:DISABLED] to={} template={}", mask(receiverTelNo), templateCode);
            return new Result(false, "DISABLED", null, "알림톡 비활성(키 미설정)");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("sejongApiKey", apiKey);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("userKey", userKey);
            body.add("senderKey", senderKey);
            body.add("templateCode", templateCode);
            body.add("receiverTelNo", receiverTelNo);
            body.add("contents", contents);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    baseUrl + "/api/v1/message/alimtalk", new HttpEntity<>(body, headers), String.class);
            Result r = parse(resp.getBody());
            log.info("[ALIMTALK:{}] to={} template={} code={}", r.success() ? "OK" : "FAIL",
                    mask(receiverTelNo), templateCode, r.code());
            return r;
        } catch (Exception e) {
            log.warn("[ALIMTALK:ERROR] to={} template={} err={}", mask(receiverTelNo), templateCode, e.getClass().getSimpleName());
            return new Result(false, "ERROR", null, e.getMessage());
        }
    }

    /** SMS 대체발송. callback = 사전등록 발신번호. 수신/발신번호는 숫자만으로 정규화(하이픈 제거). */
    public Result sendSms(String userKey, String receiverTelNo, String callback, String contents) {
        if (apiKey == null || apiKey.isBlank()) return new Result(false, "DISABLED", null, "키 미설정");
        String to = digits(receiverTelNo);
        String from = digits(callback);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("sejongApiKey", apiKey);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("userKey", userKey);
            body.add("receiverTelNo", to);
            body.add("callback", from);
            body.add("contents", contents);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    baseUrl + "/api/v1/message/sms", new HttpEntity<>(body, headers), String.class);
            Result r = parse(resp.getBody());
            log.info("[SMS:{}] to={} from={} code={} msg={}", r.success() ? "OK" : "FAIL", mask(to), from, r.code(), r.message());
            return r;
        } catch (Exception e) {
            log.warn("[SMS:ERROR] to={} err={}", mask(to), e.getMessage());
            return new Result(false, "ERROR", null, e.getMessage());
        }
    }

    private static String digits(String s) { return s == null ? "" : s.replaceAll("[^0-9]", ""); }

    private Result parse(String bodyStr) throws Exception {
        JsonNode j = objectMapper.readTree(bodyStr);
        String code = j.path("code").asText();
        String sendCode = j.path("sendCode").asText(null);
        String message = j.path("message").asText();
        return new Result("200".equals(code), code, sendCode, message);
    }

    /** 로그용 전화 마스킹 010-1234-****. */
    static String mask(String phone) {
        if (phone == null) return "***";
        String d = phone.replaceAll("[^0-9]", "");
        if (d.length() < 8) return "***";
        return d.substring(0, 3) + "-" + d.substring(3, d.length() - 4) + "-****";
    }
}
