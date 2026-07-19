package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P5-W2 근접 동료 응답([제가 갑니다]) — alert×person 1행(멱등). 골든타임 증거사슬의 t2.
 * response 는 현재 GOING 유일값. 관제 "N명 응답 — ○○○ 이동중" 표시의 원천.
 */
@Entity
@Table(name = "safety_alert_responses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SafetyAlertResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(nullable = false, length = 16)
    private String response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
