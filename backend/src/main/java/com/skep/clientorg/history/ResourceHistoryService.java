package com.skep.clientorg.history;

import com.skep.clientorg.ClientOrg;
import com.skep.clientorg.ClientOrgRepository;
import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 자원(장비/인원) ClientOrg 이력 관리.
 *  - ADMIN: 수동 등록/수정/삭제.
 *  - 작업계획서 STARTED hook: 작업계획서가 ClientOrg 와 연결됐을 때 배정 자원에 자동 이력 추가.
 *  - 조회: 자원별 이력 + ClientOrg 별 chip 카운트.
 */
@Service
@Transactional
public class ResourceHistoryService {

    private final EquipmentClientOrgHistoryRepository eqHistRepo;
    private final PersonClientOrgHistoryRepository ppHistRepo;
    private final ClientOrgRepository clientOrgs;

    public ResourceHistoryService(EquipmentClientOrgHistoryRepository eqHistRepo,
                                   PersonClientOrgHistoryRepository ppHistRepo,
                                   ClientOrgRepository clientOrgs) {
        this.eqHistRepo = eqHistRepo;
        this.ppHistRepo = ppHistRepo;
        this.clientOrgs = clientOrgs;
    }

    // ── ADMIN 수동 ─────────────────────────────────────────────

    public HistoryDto addEquipmentHistory(Long equipmentId, HistoryUpsertRequest req, AuthenticatedUser actor) {
        ClientOrg co = clientOrgOrThrow(req.clientOrgId());
        validatePeriod(req.periodStart(), req.periodEnd());
        EquipmentClientOrgHistory h = eqHistRepo.save(EquipmentClientOrgHistory.builder()
                .equipmentId(equipmentId)
                .clientOrgId(co.getId())
                .periodStart(req.periodStart())
                .periodEnd(req.periodEnd())
                .source(HistorySource.ADMIN)
                .createdBy(actor.id())
                .build());
        return toDto(h.getId(), co, h.getPeriodStart(), h.getPeriodEnd(), h.getSource());
    }

    public HistoryDto updateEquipmentHistory(Long historyId, HistoryUpsertRequest req) {
        EquipmentClientOrgHistory h = eqHistRepo.findById(historyId).orElseThrow(() ->
                ApiException.notFound("HISTORY_NOT_FOUND", "이력 " + historyId + " 없음"));
        validatePeriod(req.periodStart(), req.periodEnd());
        h.updatePeriod(req.periodStart(), req.periodEnd());
        ClientOrg co = clientOrgs.findById(h.getClientOrgId()).orElse(null);
        return toDto(h.getId(), co, h.getPeriodStart(), h.getPeriodEnd(), h.getSource());
    }

    public void deleteEquipmentHistory(Long historyId) {
        eqHistRepo.deleteById(historyId);
    }

    public HistoryDto addPersonHistory(Long personId, HistoryUpsertRequest req, AuthenticatedUser actor) {
        ClientOrg co = clientOrgOrThrow(req.clientOrgId());
        validatePeriod(req.periodStart(), req.periodEnd());
        PersonClientOrgHistory h = ppHistRepo.save(PersonClientOrgHistory.builder()
                .personId(personId)
                .clientOrgId(co.getId())
                .periodStart(req.periodStart())
                .periodEnd(req.periodEnd())
                .source(HistorySource.ADMIN)
                .createdBy(actor.id())
                .build());
        return toDto(h.getId(), co, h.getPeriodStart(), h.getPeriodEnd(), h.getSource());
    }

    public HistoryDto updatePersonHistory(Long historyId, HistoryUpsertRequest req) {
        PersonClientOrgHistory h = ppHistRepo.findById(historyId).orElseThrow(() ->
                ApiException.notFound("HISTORY_NOT_FOUND", "이력 " + historyId + " 없음"));
        validatePeriod(req.periodStart(), req.periodEnd());
        h.updatePeriod(req.periodStart(), req.periodEnd());
        ClientOrg co = clientOrgs.findById(h.getClientOrgId()).orElse(null);
        return toDto(h.getId(), co, h.getPeriodStart(), h.getPeriodEnd(), h.getSource());
    }

    public void deletePersonHistory(Long historyId) {
        ppHistRepo.deleteById(historyId);
    }

    // ── 자동 (작업계획서 STARTED hook) ────────────────────────

    /** WorkPlanService.startWorkPlan() 에서 호출. ClientOrg 없으면 no-op. */
    public void recordWorkPlanStart(Long workPlanId, Long clientOrgId,
                                     LocalDate periodStart, LocalDate periodEnd,
                                     List<Long> equipmentIds, List<Long> personIds) {
        if (clientOrgId == null) return;
        for (Long eqId : equipmentIds) {
            if (eqHistRepo.countWorkPlanSource(eqId, clientOrgId, workPlanId) > 0) continue;
            eqHistRepo.save(EquipmentClientOrgHistory.builder()
                    .equipmentId(eqId).clientOrgId(clientOrgId)
                    .periodStart(periodStart).periodEnd(periodEnd)
                    .source(HistorySource.WORK_PLAN).sourceRefId(workPlanId).build());
        }
        for (Long pId : personIds) {
            if (ppHistRepo.countWorkPlanSource(pId, clientOrgId, workPlanId) > 0) continue;
            ppHistRepo.save(PersonClientOrgHistory.builder()
                    .personId(pId).clientOrgId(clientOrgId)
                    .periodStart(periodStart).periodEnd(periodEnd)
                    .source(HistorySource.WORK_PLAN).sourceRefId(workPlanId).build());
        }
    }

    // ── 조회 ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HistoryDto> listEquipmentHistory(Long equipmentId) {
        Map<Long, ClientOrg> coCache = orgCache();
        return eqHistRepo.findByEquipmentIdOrderByPeriodStartDesc(equipmentId).stream()
                .map(h -> toDto(h.getId(), coCache.get(h.getClientOrgId()),
                        h.getPeriodStart(), h.getPeriodEnd(), h.getSource()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryDto> listPersonHistory(Long personId) {
        Map<Long, ClientOrg> coCache = orgCache();
        return ppHistRepo.findByPersonIdOrderByPeriodStartDesc(personId).stream()
                .map(h -> toDto(h.getId(), coCache.get(h.getClientOrgId()),
                        h.getPeriodStart(), h.getPeriodEnd(), h.getSource()))
                .toList();
    }

    /** 자원 후보 정렬용 — equipmentIds 의 (equipmentId → 가진 ClientOrg ID Set). */
    @Transactional(readOnly = true)
    public Map<Long, Set<Long>> equipmentClientOrgSets(Collection<Long> equipmentIds) {
        if (equipmentIds.isEmpty()) return Map.of();
        return eqHistRepo.findByEquipmentIdIn(equipmentIds).stream()
                .collect(Collectors.groupingBy(
                        EquipmentClientOrgHistory::getEquipmentId,
                        Collectors.mapping(EquipmentClientOrgHistory::getClientOrgId, Collectors.toSet())));
    }

    @Transactional(readOnly = true)
    public Map<Long, Set<Long>> personClientOrgSets(Collection<Long> personIds) {
        if (personIds.isEmpty()) return Map.of();
        return ppHistRepo.findByPersonIdIn(personIds).stream()
                .collect(Collectors.groupingBy(
                        PersonClientOrgHistory::getPersonId,
                        Collectors.mapping(PersonClientOrgHistory::getClientOrgId, Collectors.toSet())));
    }

    // ── helpers ─────────────────────────────────────────────

    private ClientOrg clientOrgOrThrow(Long id) {
        return clientOrgs.findById(id).orElseThrow(() ->
                ApiException.badRequest("CLIENT_ORG_NOT_FOUND", "원청기관 " + id + " 없음"));
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null) throw ApiException.badRequest("PERIOD_REQUIRED", "시작일 필수");
        if (end != null && end.isBefore(start)) {
            throw ApiException.badRequest("INVALID_PERIOD", "종료일이 시작일보다 앞섭니다");
        }
    }

    private Map<Long, ClientOrg> orgCache() {
        return clientOrgs.findAll().stream().collect(Collectors.toMap(ClientOrg::getId, c -> c));
    }

    private HistoryDto toDto(Long id, ClientOrg co, LocalDate s, LocalDate e, HistorySource src) {
        return new HistoryDto(id, co != null ? co.getId() : null,
                co != null ? co.getName() : "(삭제됨)", s, e, src);
    }
}
