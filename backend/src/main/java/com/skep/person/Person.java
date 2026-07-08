package com.skep.person;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "persons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(nullable = false, length = 100)
    private String name;

    private LocalDate birth;

    @Column(length = 32)
    private String phone;

    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(name = "photo_content_type", length = 100)
    private String photoContentType;

    @Column(name = "attendance_code", length = 8, unique = true)
    private String attendanceCode;

    public void assignAttendanceCode(String code) { this.attendanceCode = code; }

    /** 공급사가 발급하는 작업자 앱 로그인 계정 (아이디/비번). 로그인 성공 시 attendanceCode 토큰을 그대로 사용. */
    @Column(length = 64)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    public void setCredentials(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /** NFC(RFC) 카드 식별자 — 카드 도착 후 등록. 출근 시 태그로 식별. */
    @Column(name = "nfc_tag_id", length = 64)
    private String nfcTagId;

    public void assignNfcTag(String tagId) {
        this.nfcTagId = (tagId == null || tagId.isBlank()) ? null : tagId.trim();
    }

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Column(name = "fcm_token_updated_at")
    private LocalDateTime fcmTokenUpdatedAt;

    public void updateFcmToken(String token) {
        this.fcmToken = token;
        this.fcmTokenUpdatedAt = LocalDateTime.now();
    }

    @Column(name = "watch_fcm_token", length = 512)
    private String watchFcmToken;

    @Column(name = "watch_fcm_token_updated_at")
    private LocalDateTime watchFcmTokenUpdatedAt;

    public void updateWatchFcmToken(String token) {
        this.watchFcmToken = token;
        this.watchFcmTokenUpdatedAt = LocalDateTime.now();
    }

    @ElementCollection(fetch = FetchType.EAGER, targetClass = PersonRole.class)
    @CollectionTable(name = "person_roles", joinColumns = @JoinColumn(name = "person_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 32)
    // N+1 회피: List<Person> 조회 시 roles 를 IN-clause 한 번에 묶어 fetch.
    @Fetch(FetchMode.SUBSELECT)
    private Set<PersonRole> roles = new HashSet<>();

    // 신규 (V9)
    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(length = 100)
    private String team;

    @Column(length = 255)
    private String qualification;

    @Column(length = 255)
    private String address;

    @Column(length = 255)
    private String email;

    @Column(name = "hired_at")
    private LocalDate hiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PersonStatus status = PersonStatus.WORKING;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    private EmploymentType employmentType = EmploymentType.DIRECT;

    // V11: 현재 배치 정보
    @Column(name = "current_site_id")
    private Long currentSiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false, length = 32)
    private PersonAssignmentStatus assignmentStatus = PersonAssignmentStatus.OFF_DUTY;

    @Column(name = "last_assigned_at")
    private LocalDateTime lastAssignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Person(Long supplierId, String name, LocalDate birth, String phone, Set<PersonRole> roles,
                   String employeeNo, String jobTitle, String team, String qualification,
                   String address, String email, LocalDate hiredAt,
                   PersonStatus status, EmploymentType employmentType, String attendanceCode) {
        this.supplierId = supplierId;
        this.name = name;
        this.birth = birth;
        this.phone = phone;
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        this.employeeNo = employeeNo;
        this.jobTitle = jobTitle;
        this.team = team;
        this.qualification = qualification;
        this.address = address;
        this.email = email;
        this.hiredAt = hiredAt;
        if (status != null) this.status = status;
        if (employmentType != null) this.employmentType = employmentType;
        this.attendanceCode = attendanceCode;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String name, LocalDate birth, String phone, Set<PersonRole> roles,
                       String employeeNo, String jobTitle, String team, String qualification,
                       String address, String email, LocalDate hiredAt,
                       PersonStatus status, EmploymentType employmentType) {
        if (name != null) this.name = name;
        if (birth != null) this.birth = birth;
        if (phone != null) this.phone = phone;
        if (roles != null) {
            this.roles.clear();
            this.roles.addAll(roles);
        }
        if (employeeNo != null) this.employeeNo = employeeNo;
        if (jobTitle != null) this.jobTitle = jobTitle;
        if (team != null) this.team = team;
        if (qualification != null) this.qualification = qualification;
        if (address != null) this.address = address;
        if (email != null) this.email = email;
        if (hiredAt != null) this.hiredAt = hiredAt;
        if (status != null) this.status = status;
        if (employmentType != null) this.employmentType = employmentType;
    }

    public void setPhoto(String key, String contentType) {
        this.photoKey = key;
        this.photoContentType = contentType;
    }

    public void clearPhoto() {
        this.photoKey = null;
        this.photoContentType = null;
    }

    /** 현장에 배치한다. assignment_status를 ON_DUTY로, current_site_id/last_assigned_at 업데이트. */
    public void assignToSite(Long siteId, LocalDateTime when) {
        this.currentSiteId = siteId;
        this.assignmentStatus = PersonAssignmentStatus.ON_DUTY;
        this.lastAssignedAt = when;
    }

    /** 현장 해제. INACTIVE는 유지하고, 그 외에는 OFF_DUTY로. */
    public void releaseFromSite() {
        this.currentSiteId = null;
        if (this.assignmentStatus != PersonAssignmentStatus.INACTIVE) {
            this.assignmentStatus = PersonAssignmentStatus.OFF_DUTY;
        }
    }

    public void setAssignmentStatus(PersonAssignmentStatus status) {
        this.assignmentStatus = status;
    }
}
