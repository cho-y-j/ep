package com.skep.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 기상청 API Hub 초단기실황(getUltraSrtNcst) 클라이언트.
 * 위경도 → 격자 변환 후 현재 기온(T1H)/습도(REH) 조회 → 체감온도 + 폭염단계.
 *
 * 발급/활용신청: https://apihub.kma.go.kr (초단기실황조회 API)
 * 인증: authKey 쿼리 파라미터.
 *
 * JDK HttpClient 사용 — Reactor Netty WebClient 가 컨테이너에서 apihub 호출 시
 * DNS/SSL 처리로 타임아웃 나는 문제 회피 (wget/OS resolver 는 정상 동작).
 */
@Component
public class KmaWeatherClient {

    private static final Logger log = LoggerFactory.getLogger(KmaWeatherClient.class);
    private static final String URL =
            "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getUltraSrtNcst";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HH = DateTimeFormatter.ofPattern("HH");

    @Value("${kma.api-key:${KMA_API_KEY:}}")
    private String apiKey;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void init() {
        log.info("KmaWeatherClient init: apiKey={}",
                (apiKey == null || apiKey.isBlank()) ? "(empty)" : "(set)");
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 현재 기온/습도 + 체감온도/폭염단계 + 풍속(m/s, WSD). windMps=null=풍속 미제공. */
    public record SiteWeather(double tempC, int humidity, double feelsLike, HeatStage stage, Double windMps) {}

    /** 위경도 기준 현재 실황 조회. 키 미설정/데이터 없음/오류 시 empty. */
    public Optional<SiteWeather> fetch(double lat, double lng) {
        if (!isEnabled()) return Optional.empty();
        int[] grid = GridConverter.toGrid(lat, lng);
        // 초단기실황: 매시 정시 생성, 제공 지연 고려해 1시간 전 정시 기준.
        LocalDateTime base = LocalDateTime.now(KST).minusHours(1);
        String baseDate = base.format(YMD);
        String baseTime = base.format(HH) + "00";
        try {
            String url = URL + "?authKey=" + apiKey
                    + "&numOfRows=10&pageNo=1&dataType=JSON"
                    + "&base_date=" + baseDate + "&base_time=" + baseTime
                    + "&nx=" + grid[0] + "&ny=" + grid[1];
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode items = mapper.readTree(resp.body() == null ? "{}" : resp.body())
                    .path("response").path("body").path("items").path("item");
            if (!items.isArray() || items.isEmpty()) {
                log.warn("KMA no data lat={} lng={} grid=({},{}) base={} {}", lat, lng, grid[0], grid[1], baseDate, baseTime);
                return Optional.empty();
            }
            Double t1h = null;
            Integer reh = null;
            Double wsd = null; // 풍속(m/s) — S1 강풍 경보.
            for (JsonNode it : items) {
                String cat = it.path("category").asText();
                String val = it.path("obsrValue").asText();
                if ("T1H".equals(cat)) t1h = parseDouble(val);
                else if ("REH".equals(cat)) reh = parseInt(val);
                else if ("WSD".equals(cat)) wsd = parseDouble(val);
            }
            if (t1h == null || reh == null) {
                log.warn("KMA missing T1H/REH lat={} lng={}", lat, lng);
                return Optional.empty();
            }
            double feels = HeatIndex.feelsLike(t1h, reh);
            return Optional.of(new SiteWeather(t1h, reh, feels, HeatStage.of(feels), wsd));
        } catch (Exception e) {
            log.warn("KMA fetch failed lat={} lng={}: {}", lat, lng, e.getMessage());
            return Optional.empty();
        }
    }

    private static Double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    private static Integer parseInt(String s) {
        try { return (int) Math.round(Double.parseDouble(s)); } catch (Exception e) { return null; }
    }
}
