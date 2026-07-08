package com.skep.onlyoffice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OnlyOffice 통합 설정. 모든 값은 환경변수로 주입.
 *
 * <pre>
 * onlyoffice.enabled=true
 * onlyoffice.server-url=https://office.example.com   # OnlyOffice Document Server 주소
 * onlyoffice.jwt-secret=xxxx                          # JWT HS256 시크릿 (Document Server 와 일치)
 * onlyoffice.public-backend-url=https://api.example.com  # OnlyOffice 가 콜백/파일 fetch 할 수 있는 우리 서버 주소
 * </pre>
 *
 * 미설정이면 {@link #enabled()} = false → status endpoint 가 disabled 응답, 프론트는 버튼 숨김.
 */
@ConfigurationProperties(prefix = "onlyoffice")
public class OnlyOfficeProperties {

    private boolean enabled = false;
    private String serverUrl;
    private String jwtSecret;
    private String publicBackendUrl;

    public boolean enabled() { return enabled && serverUrl != null && !serverUrl.isBlank(); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public String getPublicBackendUrl() { return publicBackendUrl; }
    public void setPublicBackendUrl(String publicBackendUrl) { this.publicBackendUrl = publicBackendUrl; }

    /**
     * 부팅 시 강제 검증 — onlyoffice.enabled=true 인데 운영 부적합 값이면 IllegalStateException.
     * 운영 사고 방지: PUBLIC_BACKEND_URL 누락/http/localhost, JWT_SECRET 약함/dev 기본값.
     */
    @jakarta.annotation.PostConstruct
    public void validate() {
        if (!enabled) return;
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalStateException("ONLYOFFICE_URL 미설정 — OnlyOffice 활성화 시 필수");
        }
        if (publicBackendUrl == null || publicBackendUrl.isBlank()) {
            throw new IllegalStateException(
                "PUBLIC_BACKEND_URL 미설정 — OnlyOffice 가 콜백/파일 fetch 못함. 예: https://skep.on1.kr");
        }
        if (!publicBackendUrl.startsWith("https://")) {
            throw new IllegalStateException(
                "PUBLIC_BACKEND_URL 은 https:// 이어야 함 (현재: " + publicBackendUrl + "). " +
                "OnlyOffice 가 http 콜백을 차단함");
        }
        String host = publicBackendUrl.toLowerCase();
        if (host.contains("://localhost") || host.contains("://127.") || host.contains("://0.0.0.0")) {
            throw new IllegalStateException(
                "PUBLIC_BACKEND_URL 이 로컬 주소 (" + publicBackendUrl + "). 운영 환경 도메인 필수");
        }
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "ONLYOFFICE_JWT_SECRET 32자 이상 필수 (현재 " + (jwtSecret == null ? 0 : jwtSecret.length()) + "자). " +
                "JWT 위변조 방지");
        }
        if (jwtSecret.startsWith("dev-") || jwtSecret.equals("changeme")) {
            throw new IllegalStateException(
                "ONLYOFFICE_JWT_SECRET 이 dev 기본값. 랜덤 32바이트 이상으로 교체 필수");
        }
    }
}
