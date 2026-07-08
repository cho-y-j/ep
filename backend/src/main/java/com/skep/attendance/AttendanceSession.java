package com.skep.attendance;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "work_plan_id", nullable = false)
    private Long workPlanId;

    @Column(name = "check_in_at", nullable = false)
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    @Column(name = "check_in_photo_doc_id")
    private Long checkInPhotoDocId;

    @Column(name = "check_out_photo_doc_id")
    private Long checkOutPhotoDocId;

    @Column(name = "check_in_photo_key", length = 255)
    private String checkInPhotoKey;

    @Column(name = "check_out_photo_key", length = 255)
    private String checkOutPhotoKey;

    /** 출근 인증 방식: CODE | BIOMETRIC | NFC (생체검증은 단말, 서버는 기록만). */
    @Column(name = "check_in_method", length = 20)
    private String checkInMethod;

    @Column(name = "break_start_at")
    private LocalDateTime breakStartAt;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes = 0;

    /** 마지막 휴식 알림(또는 휴식 종료) 시각 — 다음 휴식 알림 기준점. */
    @Column(name = "last_rest_alert_at")
    private LocalDateTime lastRestAlertAt;

    public void startBreak() {
        if (breakStartAt != null) throw new IllegalStateException("이미 휴식 중입니다");
        this.breakStartAt = LocalDateTime.now();
    }

    public void endBreak() {
        if (breakStartAt == null) throw new IllegalStateException("휴식 상태가 아닙니다");
        LocalDateTime now = LocalDateTime.now();
        long mins = java.time.Duration.between(breakStartAt, now).toMinutes();
        this.breakMinutes = (breakMinutes == null ? 0 : breakMinutes) + (int) mins;
        this.breakStartAt = null;
        this.lastRestAlertAt = now; // 휴식 종료 → 다음 휴식 알림 기준 시각 리셋
    }

    /** 휴식 알림 발송 처리 — 기준 시각 갱신. */
    public void markRestAlerted(LocalDateTime when) {
        this.lastRestAlertAt = when;
    }

    /** 지정 작업시간(출근시각과 별개) — 현장 관리자가 지정·수정. 휴식 타이머 기준. */
    @Column(name = "work_start_at")
    private LocalDateTime workStartAt;

    @Column(name = "work_end_at")
    private LocalDateTime workEndAt;

    public void setWorkTime(LocalDateTime start, LocalDateTime end) {
        this.workStartAt = start;
        this.workEndAt = end;
    }

    @Column(name = "check_in_lat")
    private Double checkInLat;
    @Column(name = "check_in_lng")
    private Double checkInLng;
    @Column(name = "check_out_lat")
    private Double checkOutLat;
    @Column(name = "check_out_lng")
    private Double checkOutLng;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private AttendanceSession(Long personId, Long workPlanId,
                              Long checkInPhotoDocId, String checkInPhotoKey,
                              Double checkInLat, Double checkInLng, String checkInMethod) {
        this.personId = personId;
        this.workPlanId = workPlanId;
        this.checkInAt = LocalDateTime.now();
        this.checkInPhotoDocId = checkInPhotoDocId;
        this.checkInPhotoKey = checkInPhotoKey;
        this.checkInLat = checkInLat;
        this.checkInLng = checkInLng;
        this.checkInMethod = (checkInMethod == null || checkInMethod.isBlank()) ? "CODE" : checkInMethod.trim();
    }

    public void checkOut(Long photoDocId, String photoKey, Double lat, Double lng) {
        this.checkOutAt = LocalDateTime.now();
        this.checkOutPhotoDocId = photoDocId;
        this.checkOutPhotoKey = photoKey;
        this.checkOutLat = lat;
        this.checkOutLng = lng;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
