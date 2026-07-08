package com.skep.field;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * FCM 발송. firebase-admin-key.json(서비스 계정 키)이 있으면 초기화, 없으면 graceful disable (로그만).
 * 키 경로: skep.fcm.credentials-path (기본 firebase-admin-key.json — 작업 디렉터리 기준).
 */
@Service
public class FieldFcmService {

    private static final Logger log = LoggerFactory.getLogger(FieldFcmService.class);
    private static final String APP_NAME = "skep-field-fcm";

    private final String credentialsPath;
    private volatile boolean enabled = false;

    public FieldFcmService(@Value("${skep.fcm.credentials-path:firebase-admin-key.json}") String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    @PostConstruct
    void init() {
        Path keyPath = Path.of(credentialsPath);
        if (!Files.exists(keyPath)) {
            log.warn("FCM disabled: credentials file not found at {} (announcements will be logged only)", credentialsPath);
            return;
        }
        try (InputStream in = new FileInputStream(keyPath.toFile())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            // 기존 다른 FirebaseApp 과 충돌하지 않도록 named app 사용.
            if (FirebaseApp.getApps().stream().noneMatch(a -> APP_NAME.equals(a.getName()))) {
                FirebaseApp.initializeApp(options, APP_NAME);
            }
            enabled = true;
            log.info("FCM enabled (firebase-admin) using {}", credentialsPath);
        } catch (Exception e) {
            log.warn("FCM init failed ({}): {}", credentialsPath, e.getMessage());
        }
    }

    /**
     * 공지를 대상 작업자 fcm_token 들로 멀티 발송. data.type=announcement, title/body 포함.
     * @return 발송 시도 토큰 수 (성공 카운트는 로그). 비활성/토큰 없음이면 0.
     */
    public int sendAnnouncement(List<String> fcmTokens, String title, String body) {
        return doSend(fcmTokens, Map.of("type", "announcement", "title", title, "body", body), title);
    }

    /** 임의 type(rest/heat/danger 등)으로 발송 — 워치/폰이 type별로 진동·표시 처리. */
    public int sendTyped(List<String> fcmTokens, String type, String title, String body) {
        return doSend(fcmTokens, Map.of("type", type, "title", title, "body", body), title);
    }

    private int doSend(List<String> fcmTokens, Map<String, String> data, String label) {
        if (fcmTokens.isEmpty()) return 0;
        if (!enabled) {
            log.info("FCM disabled — not sent (title='{}', targets={})", label, fcmTokens.size());
            return 0;
        }
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(FirebaseApp.getInstance(APP_NAME));
        int sent = 0;
        for (String token : fcmTokens) {
            Message message = Message.builder()
                    .setToken(token)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            try {
                messaging.send(message);
                sent++;
            } catch (Exception e) {
                log.warn("FCM send failed (token …{}): {}",
                        token.length() > 6 ? token.substring(token.length() - 6) : token, e.getMessage());
            }
        }
        log.info("FCM sent {}/{} (title='{}')", sent, fcmTokens.size(), label);
        return fcmTokens.size();
    }
}
