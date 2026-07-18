package com.skep.legalinspection.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 점검원(모바일웹) 흐름 DTO — 대상 목록·NFC 오픈·제출·증거요약. */
public final class InspectorDtos {

    private InspectorDtos() {}

    /** 오늘 점검 대상 — 배치(current_site_id) 장비 1건. */
    public record TargetItem(
            Long equipmentId,
            String vehicleNo,
            String model,
            String category,
            Long siteId,
            String siteName,
            boolean hasNfcTag,
            boolean doneToday
    ) {}

    /** NFC 태그 오픈 요청. source = NFC(실태그) | MANUAL(폴백, tag_verified=false). */
    public record OpenRequest(Long equipmentId, String tagId, String source) {}

    /** 오픈 응답 — 오픈 토큰 + 증거(태그 시각·검증여부) + 렌더링용 장비/템플릿. */
    public record OpenResponse(
            String openToken,
            LocalDateTime tagReadAt,
            boolean tagVerified,
            Long equipmentId,
            String equipmentLabel,
            SafetyCheckTemplateDtos.Response template
    ) {}

    /** 제출 요청 — 오픈 토큰 + 항목 결과 + 서명. itemsResult = [{no, checked, na, note}]. */
    public record SubmitRequest(
            String openToken,
            List<Map<String, Object>> itemsResult,
            String signPngBase64,
            String memo
    ) {}

    /** 제출 응답 — 증거 요약. */
    public record EvidenceResponse(
            Long id,
            Long equipmentId,
            LocalDate inspectDate,
            LocalDateTime tagReadAt,
            boolean tagVerified,
            String inspectorName,
            boolean signed,
            LocalDateTime createdAt
    ) {}

    /** BP 안전 허브 — 현장 오늘 법정점검 현황(완료율·미점검 장비). */
    public record BpStatus(
            int target,
            int done,
            List<PendingEquipment> pending
    ) {}

    public record PendingEquipment(Long equipmentId, String label) {}
}
