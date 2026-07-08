package com.example.verifyapi.rims;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rims")
public class RimsProperties {

    // RIMS API Base URL
    private String baseUrl;

    // RIMS 사업자 인증용 Basic Key (OAuth2 token 발급 시 사용)
    private String authKey;

    // AES-128-ECB 암호화용 Secret Key (16 bytes)
    private String secretKey;

    // 서비스 경로 prefix (예: /cmm, /sub, /dapi)
    private String servicePath = "";
    private int timeout = 5000;
    private long tokenTtlSeconds = 10800; // 3시간

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getServicePath() {
        return servicePath;
    }

    public void setServicePath(String servicePath) {
        this.servicePath = servicePath;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && authKey != null && !authKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
