package com.skep.quotation.snapshot;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 선정 시점의 비교 증거 동결.
 * snapshot_json: 응찰한 공급사들의 list + 각 가격 + 노트 + 제출 시점 JSON.
 * 영구 보존 — 응찰자가 나중에 제안을 수정/삭제해도 이 snapshot 은 변하지 않음.
 */
@Entity
@Table(name = "quotation_comparison_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComparisonSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quotation_request_id", nullable = false)
    private Long quotationRequestId;

    @Column(name = "selected_proposal_id")
    private Long selectedProposalId;

    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;

    @Column(name = "selected_by")
    private Long selectedBy;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "selection_reason", columnDefinition = "TEXT")
    private String selectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ComparisonSnapshot(Long quotationRequestId, Long selectedProposalId, Long selectedBy,
                               String snapshotJson, String selectionReason) {
        this.quotationRequestId = quotationRequestId;
        this.selectedProposalId = selectedProposalId;
        this.selectedBy = selectedBy;
        this.snapshotJson = snapshotJson;
        this.selectionReason = selectionReason;
    }

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.selectedAt = now;
        this.createdAt = now;
    }
}
