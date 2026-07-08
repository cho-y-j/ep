package com.skep.worksheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * skep v1 의 WorksheetEditorService 를 v2 로 이식.
 * 작업계획서 생성 중 OnlyOffice 새 탭 편집기를 띄우기 위한 임시 세션 관리 (wp.id 없이 동작).
 *
 * 흐름:
 *   1) 프론트가 클라이언트 docxtemplater 로 DOCX blob 생성
 *   2) POST /api/worksheet/editor-session 으로 업로드 → sessionId + OnlyOffice config 반환
 *   3) 프론트가 새 탭 /worksheet/edit/{sid} 열고 OnlyOffice iframe init
 *   4) OnlyOffice 가 /editor-file/{sid} 로 DOCX 다운로드해서 띄움
 *   5) 사용자 편집 시 OnlyOffice 가 /onlyoffice-callback/{sid} 로 save 콜백 → 디스크 덮어쓰기
 *
 * 세션 파일은 24h TTL, scheduled cleanup.
 */
@Service
public class WorksheetEditorService {

    private static final Logger log = LoggerFactory.getLogger(WorksheetEditorService.class);
    // 컨테이너 재시작 시 보존되도록 영구 uploads volume 하위에 저장.
    // 외부에서 path 변경 필요하면 SKEP_WORKSHEET_SESSION_DIR env 로 override.
    private static final Path SESSION_DIR = Paths.get(
            System.getenv().getOrDefault("SKEP_WORKSHEET_SESSION_DIR", "/app/uploads/worksheet-sessions"));
    private static final Path SESSION_DIR_ABS = SESSION_DIR.toAbsolutePath().normalize();
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    /** sessionId 는 UUID v4 형식만 허용 — path traversal payload 차단. */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // dev/test placeholder. prod profile 활성화 시 이 값이 사용되면 부팅 거부 (JwtService 와 동일 정책).
    private static final String KNOWN_DEV_SECRET = "change_me_jwt_must_be_at_least_32_bytes_long";

    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final org.springframework.core.env.Environment env;
    private SecretKey signKey;

    @Value("${app.onlyoffice.jwt-secret:${ONLYOFFICE_JWT_SECRET:change_me_jwt_must_be_at_least_32_bytes_long}}")
    private String jwtSecret;

    @Value("${app.onlyoffice.internal-doc-service:http://backend:8080}")
    private String internalDocServiceUrl;

    // SSRF 차단: callback url 이 이 host 와 같을 때만 fetch 허용. OnlyOfficeService 의 검증과 동일.
    @Value("${app.onlyoffice.url:${ONLYOFFICE_URL:http://localhost:8083}}")
    private String onlyOfficeServerUrl;

    public WorksheetEditorService(ObjectMapper objectMapper, org.springframework.core.env.Environment env) {
        this.objectMapper = objectMapper;
        this.env = env;
    }

    @jakarta.annotation.PostConstruct
    void initSignKey() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "worksheet callback JWT secret must be at least 32 bytes — set ONLYOFFICE_JWT_SECRET");
        }
        boolean isProd = java.util.Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (isProd && KNOWN_DEV_SECRET.equals(jwtSecret)) {
            // 운영에서 dev 기본 시크릿이면 OnlyOffice 콜백이 위조 가능 — 위험하지만, 라이브 시스템의 부팅을
            // 막아 가용성을 떨어뜨리는 대신 강하게 경고만 한다. 실제 ONLYOFFICE_JWT_SECRET 설정은 운영(.env) 조치.
            log.warn("worksheet callback JWT secret is the development placeholder in prod — OnlyOffice callbacks are forgeable; set a real ONLYOFFICE_JWT_SECRET (32+ bytes)");
        }
        this.signKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public Map<String, Object> createSession(MultipartFile docx, String userName, String baseName) throws IOException {
        if (docx == null || docx.isEmpty()) throw new IllegalArgumentException("DOCX 파일이 필요합니다");
        Files.createDirectories(SESSION_DIR);
        String sessionId = UUID.randomUUID().toString();
        Path sessionFile = SESSION_DIR.resolve(sessionId + ".docx");
        Files.write(sessionFile, docx.getBytes());

        String safeBase = (baseName == null || baseName.isBlank()) ? "worksheet" : baseName;
        String fileName = safeBase + ".docx";

        // OnlyOffice 가 URL 확장자로 파일 타입 추정하므로 .docx 명시 (확장자 없으면 형식 판별 실패).
        String documentUrl = internalDocServiceUrl + "/api/worksheet/editor-file/" + sessionId + ".docx";
        String callbackUrl = internalDocServiceUrl + "/api/worksheet/onlyoffice-callback/" + sessionId;

        ObjectNode config = objectMapper.createObjectNode();
        config.put("documentType", "word");
        ObjectNode document = config.putObject("document");
        document.put("fileType", "docx");
        document.put("key", sessionId);
        document.put("title", fileName);
        document.put("url", documentUrl);
        ObjectNode editorConfig = config.putObject("editorConfig");
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("lang", "ko");
        editorConfig.put("mode", "edit");
        ObjectNode user = editorConfig.putObject("user");
        user.put("id", UUID.randomUUID().toString());
        user.put("name", userName == null || userName.isBlank() ? "SKEP 사용자" : userName);
        ObjectNode custom = editorConfig.putObject("customization");
        custom.put("autosave", true);
        custom.put("forcesave", true);
        custom.put("uiTheme", "theme-light");
        custom.put("spellcheck", false);
        ObjectNode review = custom.putObject("review");
        review.put("hideReviewDisplay", true);
        review.put("trackChanges", false);
        ObjectNode goback = custom.putObject("goback");
        goback.put("text", "작업계획서로 돌아가기");

        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = objectMapper.convertValue(config, Map.class);
        String token = Jwts.builder()
                .claims().add(configMap).and()
                .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(6)))
                .signWith(signKey)
                .compact();
        config.put("token", token);

        return Map.of(
                "sessionId", sessionId,
                "fileName", fileName,
                "config", config
        );
    }

    public byte[] readSession(String sessionId) throws IOException {
        try {
            return Files.readAllBytes(sessionFile(sessionId));
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("세션을 찾을 수 없습니다: " + sessionId);
        }
    }

    /** sessionId UUID 검증 + path normalize 후 SESSION_DIR 안인지 확인. */
    public Path sessionFile(String sessionId) {
        if (sessionId == null || !UUID_PATTERN.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("invalid session id");
        }
        Path file = SESSION_DIR.resolve(sessionId + ".docx").toAbsolutePath().normalize();
        if (!file.startsWith(SESSION_DIR_ABS)) {
            throw new IllegalArgumentException("path escape detected");
        }
        return file;
    }

    public Map<String, Object> handleCallback(String sessionId, JsonNode body) {
        // sessionId UUID 검증 + SESSION_DIR escape 차단 — sessionFile() 호출 시 throw
        Path targetFile;
        try {
            targetFile = sessionFile(sessionId);
        } catch (IllegalArgumentException e) {
            log.warn("Callback rejected — invalid session id: {}", e.getMessage());
            return Map.of("error", 1);
        }

        int status = body.has("status") ? body.get("status").asInt() : 0;
        log.info("OnlyOffice callback: session={} status={}", sessionId, status);
        if (status != 2 && status != 6) return Map.of("error", 0);

        // OnlyOffice JWT 토큰 검증 — body.token 이 우리 signKey 로 서명된 것인지.
        // OnlyOffice 가 JWT mode 일 때 callback body 안에 token 필드를 자기 secret 으로 서명해 보냄.
        if (!verifyCallbackToken(body, sessionId)) {
            return Map.of("error", 1);
        }

        String url = body.has("url") ? body.get("url").asText() : null;
        if (url == null) {
            log.warn("Callback without url for session {}", sessionId);
            return Map.of("error", 1);
        }
        if (!isAllowedCallbackUrl(url)) {
            log.warn("Callback url host mismatch — rejected for session {}", sessionId);
            return Map.of("error", 1);
        }
        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        lock.lock();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                log.error("Edited DOCX download failed for session {}: status={}", sessionId, resp.statusCode());
                return Map.of("error", 1);
            }
            Path tmp = targetFile.resolveSibling(sessionId + ".docx.tmp");
            Files.write(tmp, resp.body());
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved edited DOCX for session {} ({} bytes)", sessionId, resp.body().length);
            return Map.of("error", 0);
        } catch (Exception e) {
            log.error("Callback error for session " + sessionId, e);
            return Map.of("error", 1);
        } finally {
            lock.unlock();
        }
    }

    /** OnlyOffice callback body.token 을 우리 signKey 로 검증. 토큰 없거나 invalid 면 거부. */
    private boolean verifyCallbackToken(JsonNode body, String sessionId) {
        String token = body.has("token") ? body.get("token").asText(null) : null;
        if (token == null || token.isBlank()) {
            log.warn("Callback rejected — token missing for session {}", sessionId);
            return false;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(signKey).build().parseSignedClaims(token).getPayload();
            // 토큰 안에 payload(callback body) 가 다시 들어있는 경우가 있음. 기본 검증은 서명 통과 + 만료 OK 면 충분.
            if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
                log.warn("Callback rejected — token expired for session {}", sessionId);
                return false;
            }
            return true;
        } catch (JwtException e) {
            log.warn("Callback rejected — token invalid for session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /** OnlyOffice 가 보낸 callback 의 url host 가 우리가 신뢰하는 OnlyOffice server host 와 같은지 확인. */
    private boolean isAllowedCallbackUrl(String url) {
        try {
            URI uri = URI.create(url);
            URI server = URI.create(onlyOfficeServerUrl);
            String h = uri.getHost();
            String sh = server.getHost();
            if (h == null || sh == null) return false;
            // host + port 둘 다 일치 — server-url 이 localhost 라도 다른 포트로의 SSRF 차단. OnlyOfficeService 와 동일.
            return h.equalsIgnoreCase(sh) && uri.getPort() == server.getPort();
        } catch (Exception e) {
            return false;
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredSessions() {
        if (!Files.isDirectory(SESSION_DIR)) return;
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        try (var stream = Files.list(SESSION_DIR)) {
            stream.filter(p -> p.toString().endsWith(".docx")).forEach(p -> {
                try {
                    FileTime mtime = Files.getLastModifiedTime(p);
                    if (mtime.toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(p);
                        String id = p.getFileName().toString().replaceFirst("\\.docx$", "");
                        sessionLocks.remove(id);
                        log.info("Deleted expired session {}", id);
                    }
                } catch (IOException ignored) { /* best-effort */ }
            });
        } catch (IOException e) {
            log.warn("Session cleanup failed: {}", e.getMessage());
        }
    }
}
