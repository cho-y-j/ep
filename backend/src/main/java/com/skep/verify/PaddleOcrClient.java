package com.skep.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 로컬 PaddleOCR(paddle-ocr FastAPI) 호출 클라이언트.
 *
 * POST {ocr.paddle.url}/ocr — multipart 필드명 image (paddle app/main.py 의 @app.post("/ocr") image 파라미터) →
 * 응답 JSON 의 fullText 만 추출.
 *
 * verify.enabled 와 무관하게 동작한다 (백필 게이트는 ocr.engine 로 별도 제어).
 * 미연결 / 타임아웃 / 5xx 시 graceful — null 반환.
 */
@Component
public class PaddleOcrClient {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrClient.class);

    @Value("${ocr.paddle.url:${PADDLE_OCR_URL:http://127.0.0.1:8100}}")
    private String paddleUrl;

    @Value("${ocr.paddle.timeout-seconds:120}")
    private long timeoutSeconds;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        // fullText 응답은 수 KB~수십 KB 수준 — 여유 있게 8MB 버퍼.
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
        log.info("PaddleOcrClient init: url={} timeout={}s", paddleUrl, timeoutSeconds);
    }

    /** 파일 바이트 → OCR fullText. 실패 / 타임아웃 시 null. */
    public String ocrFullText(byte[] fileBytes, String filename) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return filename; }
            }).contentType(MediaType.APPLICATION_OCTET_STREAM);

            String response = webClient.post()
                    .uri(paddleUrl + "/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            if (response == null) return null;
            JsonNode node = objectMapper.readTree(response);
            JsonNode ft = node.get("fullText");
            return ft != null && ft.isTextual() ? ft.asText() : null;
        } catch (Exception e) {
            log.warn("paddle ocr call failed url={} error={}", paddleUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 영역-크롭 OCR. 파일 + 템플릿(JSON) + (선택) 코너 4점(JSON) → /extract-regions →
     * 응답 fields(key→value) 를 Map 으로. 실패 / 타임아웃 시 null.
     */
    public Map<String, String> extractRegions(byte[] fileBytes, String filename,
                                               String cornersJson, String templateJson) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return filename; }
            }).contentType(MediaType.APPLICATION_OCTET_STREAM);
            builder.part("template", templateJson);
            if (cornersJson != null && !cornersJson.isBlank()) {
                builder.part("corners", cornersJson);
            }

            String response = webClient.post()
                    .uri(paddleUrl + "/extract-regions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            if (response == null) return null;
            JsonNode fields = objectMapper.readTree(response).get("fields");
            if (fields == null || !fields.isObject()) return null;
            Map<String, String> map = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode v = e.getValue();
                if (v != null && !v.isNull()) map.put(e.getKey(), v.asText());
            }
            return map;
        } catch (Exception e) {
            log.warn("paddle extract-regions call failed url={} error={}", paddleUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 영역-크롭 OCR (raw). 파일 + 템플릿(JSON) + (선택) 코너 + return_warped → /extract-regions →
     * 응답 JSON({aligned, fields, regions, warped_image_base64?}) 전체를 그대로 반환. 실패 / 타임아웃 시 null.
     * 수퍼어드민 영역지정 도구용 — {@link #extractRegions} 의 Map 요약과 달리 warp 이미지/score 등 원문이 필요.
     */
    public JsonNode extractRegionsRaw(byte[] fileBytes, String filename,
                                      String cornersJson, String templateJson, boolean returnWarped) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return filename; }
            }).contentType(MediaType.APPLICATION_OCTET_STREAM);
            builder.part("template", templateJson);
            if (cornersJson != null && !cornersJson.isBlank()) {
                builder.part("corners", cornersJson);
            }
            builder.part("return_warped", String.valueOf(returnWarped));

            String response = webClient.post()
                    .uri(paddleUrl + "/extract-regions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            if (response == null) return null;
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.warn("paddle extract-regions(raw) call failed url={} error={}", paddleUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 문서 4모서리 자동 검출. 파일 → /detect-corners → 응답 JSON({detected,corners,image_size}) 그대로.
     * 실패 / 타임아웃 시 null.
     */
    public JsonNode detectCorners(byte[] fileBytes, String filename) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return filename; }
            }).contentType(MediaType.APPLICATION_OCTET_STREAM);

            String response = webClient.post()
                    .uri(paddleUrl + "/detect-corners")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            if (response == null) return null;
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.warn("paddle detect-corners call failed url={} error={}", paddleUrl, e.getMessage());
            return null;
        }
    }
}
