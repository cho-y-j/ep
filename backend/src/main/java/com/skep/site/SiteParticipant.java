package com.skep.site;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "site_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 32)
    private SiteParticipantType participantType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SiteParticipantStatus status;

    @Column(name = "added_by")
    private Long addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Builder
    private SiteParticipant(Long siteId, Long companyId, SiteParticipantType participantType,
                            SiteParticipantStatus status, Long addedBy) {
        this.siteId = siteId;
        this.companyId = companyId;
        this.participantType = participantType;
        this.status = status != null ? status : SiteParticipantStatus.ACTIVE;
        this.addedBy = addedBy;
    }

    @PrePersist
    void onCreate() {
        this.addedAt = LocalDateTime.now();
    }

    public void activate() {
        this.status = SiteParticipantStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = SiteParticipantStatus.INACTIVE;
    }
}
