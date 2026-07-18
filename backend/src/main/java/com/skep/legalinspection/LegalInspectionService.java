package com.skep.legalinspection;

import com.skep.common.ApiException;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.legalinspection.InspectionOpenToken.Payload;
import com.skep.legalinspection.dto.InspectorDtos.*;
import com.skep.legalinspection.dto.SafetyCheckTemplateDtos;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * S2′ 법정점검 — NFC 강제 흐름(태그 검증→오픈 토큰→체크리스트+서명 제출) + 관제 집계.
 * 조종원 일일점검(DailyEquipmentInspection)과 별도 트랙. 증거사슬: tag_read_at·tag_verified·서명.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LegalInspectionService {

    private static final String TARGET_EQUIPMENT = "EQUIPMENT";

    private final LegalInspectionRepository repo;
    private final SafetyCheckTemplateRepository templates;
    private final EquipmentRepository equipments;
    private final SiteRepository sites;
    private final PersonRepository persons;
    private final InspectionOpenToken openToken;

    // ── 점검원(모바일웹) 흐름 ──────────────────────────────────────────

    /** 점검원 화면 렌더용 활성 EQUIPMENT 템플릿. */
    @Transactional(readOnly = true)
    public SafetyCheckTemplateDtos.Response activeTemplate() {
        SafetyCheckTemplate t = templates.findFirstByTargetAndActiveTrueOrderByIdDesc(TARGET_EQUIPMENT)
                .orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_TEMPLATE", "활성 점검 템플릿이 없습니다"));
        return SafetyCheckTemplateDtos.Response.from(t);
    }

    /** 오늘 점검 대상 — 배치(current_site_id) 장비. siteId 지정 시 해당 현장만. */
    @Transactional(readOnly = true)
    public List<TargetItem> targets(Long siteFilter) {
        List<Equipment> deployed = equipments.findByCurrentSiteIdIsNotNullOrderByIdDesc();
        if (siteFilter != null) {
            deployed = deployed.stream().filter(e -> siteFilter.equals(e.getCurrentSiteId())).toList();
        }
        if (deployed.isEmpty()) return List.of();

        List<Long> eqIds = deployed.stream().map(Equipment::getId).toList();
        Set<Long> doneToday = repo.findByEquipmentIdInAndInspectDate(eqIds, LocalDate.now()).stream()
                .map(LegalInspection::getEquipmentId).collect(Collectors.toSet());
        Map<Long, String> siteNames = siteNames(deployed.stream()
                .map(Equipment::getCurrentSiteId).filter(Objects::nonNull).distinct().toList());

        return deployed.stream().map(e -> new TargetItem(
                e.getId(), e.getVehicleNo(), e.getModel(), e.getCategory(),
                e.getCurrentSiteId(), siteNames.get(e.getCurrentSiteId()),
                e.getNfcTagId() != null && !e.getNfcTagId().isBlank(),
                doneToday.contains(e.getId())
        )).toList();
    }

    /**
     * NFC 태그 → 서버 검증 → 오픈 토큰 발급. 태그가 장비와 불일치하면 403(위조 차단).
     * source=NFC 만 실제 태그 증명(tag_verified=true), MANUAL 폴백은 false 로 구분 기록.
     */
    public OpenResponse open(Person inspector, OpenRequest req) {
        if (req == null || req.equipmentId() == null) {
            throw ApiException.badRequest("NO_EQUIPMENT", "equipment_id 필수");
        }
        String submittedTag = req.tagId() == null ? "" : req.tagId().trim();
        if (submittedTag.isBlank()) throw ApiException.badRequest("NO_TAG", "태그 값이 필요합니다");

        Equipment e = equipments.findById(req.equipmentId())
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비를 찾을 수 없습니다"));
        if (e.getNfcTagId() == null || e.getNfcTagId().isBlank()) {
            throw ApiException.badRequest("NO_NFC_TAG", "장비에 NFC 태그가 등록되지 않았습니다");
        }
        if (!e.getNfcTagId().equals(submittedTag)) {
            throw ApiException.forbidden("TAG_MISMATCH", "태그가 이 장비와 일치하지 않습니다");
        }

        SafetyCheckTemplate template = templates.findFirstByTargetAndActiveTrueOrderByIdDesc(TARGET_EQUIPMENT)
                .orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_TEMPLATE", "활성 점검 템플릿이 없습니다"));

        boolean verified = "NFC".equalsIgnoreCase(req.source());
        LocalDateTime readAt = LocalDateTime.now();
        String token = openToken.issue(e.getId(), inspector.getId(), template.getId(), verified, submittedTag, readAt);
        return new OpenResponse(token, readAt, verified, e.getId(), equipmentLabel(e),
                SafetyCheckTemplateDtos.Response.from(template));
    }

    /** 체크리스트 제출 — 오픈 토큰 재검증 + 필수항목 가드 + 서명 필수. 증거 요약 반환. */
    public EvidenceResponse submit(Person inspector, SubmitRequest req) {
        Payload payload = openToken.verify(req.openToken());
        if (!payload.inspectorPersonId().equals(inspector.getId())) {
            throw ApiException.forbidden("OPEN_TOKEN_OWNER", "본인의 오픈 토큰이 아닙니다");
        }
        Equipment e = equipments.findById(payload.equipmentId())
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비를 찾을 수 없습니다"));
        SafetyCheckTemplate template = templates.findById(payload.templateId())
                .orElseThrow(() -> ApiException.badRequest("TEMPLATE_NOT_FOUND", "점검 템플릿을 찾을 수 없습니다"));

        List<Map<String, Object>> result = req.itemsResult() != null ? req.itemsResult() : List.of();
        validateRequiredItems(template.getItems(), result);

        if (req.signPngBase64() == null || req.signPngBase64().isBlank()) {
            throw ApiException.badRequest("NO_SIGNATURE", "점검원 서명이 필요합니다");
        }
        byte[] sign;
        try {
            String b64 = req.signPngBase64();
            int comma = b64.indexOf(',');
            if (b64.startsWith("data:") && comma > 0) b64 = b64.substring(comma + 1);
            sign = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("BAD_SIGNATURE", "서명 이미지 base64 디코딩 실패");
        }

        LocalDate inspectDate = payload.readAt().toLocalDate();
        repo.findByEquipmentIdAndInspectDateAndTemplateId(e.getId(), inspectDate, template.getId())
                .ifPresent(x -> { throw ApiException.conflict("ALREADY_INSPECTED", "오늘 이 장비의 법정점검이 이미 등록되었습니다"); });

        LegalInspection row = new LegalInspection();
        row.setEquipmentId(e.getId());
        row.setSiteId(e.getCurrentSiteId());
        row.setInspectorPersonId(inspector.getId());
        row.setInspectDate(inspectDate);
        row.setTemplateId(template.getId());
        row.setTagIdSubmitted(payload.tagId());
        row.setTagVerified(payload.verified());
        row.setTagReadAt(payload.readAt());
        row.setItemsResult(new ArrayList<>(result));
        row.setSignPng(sign);
        row.setMemo(trimToNull(req.memo()));
        repo.save(row);

        return new EvidenceResponse(row.getId(), e.getId(), inspectDate, row.getTagReadAt(),
                row.isTagVerified(), inspector.getName(), true, row.getCreatedAt());
    }

    // ── BP 안전 허브 / 원청 관제 집계 ─────────────────────────────────

    /** BP 현장 오늘 법정점검 현황 — 배치 장비 중 완료/대상 + 미점검 목록. ADMIN=전체, BP=본인 현장. */
    @Transactional(readOnly = true)
    public BpStatus bpSiteStatus(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        if (actor.role() != Role.ADMIN) {
            if (actor.role() != Role.BP || actor.companyId() == null
                    || !actor.companyId().equals(site.getBpCompanyId())) {
                throw ApiException.forbidden("SITE_DENIED", "본인 현장만 조회할 수 있습니다");
            }
        }
        List<Equipment> deployed = equipments.findByCurrentSiteIdIsNotNullOrderByIdDesc().stream()
                .filter(e -> siteId.equals(e.getCurrentSiteId())).toList();
        Set<Long> done = deployed.isEmpty() ? Set.of()
                : repo.findByEquipmentIdInAndInspectDate(deployed.stream().map(Equipment::getId).toList(), LocalDate.now())
                    .stream().map(LegalInspection::getEquipmentId).collect(Collectors.toSet());
        List<PendingEquipment> pending = deployed.stream()
                .filter(e -> !done.contains(e.getId()))
                .map(e -> new PendingEquipment(e.getId(), equipmentLabel(e)))
                .toList();
        return new BpStatus(deployed.size(), done.size(), pending);
    }

    /** 원청 관제 — 지정 장비들의 오늘 법정점검 완료 장비 수(distinct). */
    @Transactional(readOnly = true)
    public int legalDoneCount(Collection<Long> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()) return 0;
        return (int) repo.findByEquipmentIdInAndInspectDate(equipmentIds, LocalDate.now()).stream()
                .map(LegalInspection::getEquipmentId).distinct().count();
    }

    // ── helpers ──────────────────────────────────────────────────────

    /** 필수 항목(required=true)은 checked=true 또는 na=true 여야 한다. */
    static void validateRequiredItems(List<Map<String, Object>> items, List<Map<String, Object>> result) {
        if (items == null) return;
        Map<Integer, Map<String, Object>> byNo = new HashMap<>();
        for (Map<String, Object> r : result) {
            Integer no = asInt(r.get("no"));
            if (no != null) byNo.put(no, r);
        }
        for (Map<String, Object> item : items) {
            if (!asBool(item.get("required"))) continue;
            Integer no = asInt(item.get("no"));
            Map<String, Object> r = no == null ? null : byNo.get(no);
            boolean done = r != null && (asBool(r.get("checked")) || asBool(r.get("na")));
            if (!done) {
                throw ApiException.badRequest("REQUIRED_ITEM_INCOMPLETE",
                        "필수 점검 항목을 모두 확인(또는 N/A)해야 제출할 수 있습니다");
            }
        }
    }

    private Map<Long, String> siteNames(List<Long> siteIds) {
        if (siteIds.isEmpty()) return Map.of();
        return sites.findAllById(siteIds).stream()
                .collect(Collectors.toMap(Site::getId, Site::getName));
    }

    private static String equipmentLabel(Equipment e) {
        if (e.getVehicleNo() != null && !e.getVehicleNo().isBlank()) return e.getVehicleNo();
        if (e.getModel() != null && !e.getModel().isBlank()) return e.getModel();
        return "장비 #" + e.getId();
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { return null; }
        }
        return null;
    }

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        return o instanceof String s && "true".equalsIgnoreCase(s.trim());
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }
}
