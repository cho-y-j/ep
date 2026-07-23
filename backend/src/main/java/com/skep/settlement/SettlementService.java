package com.skep.settlement;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.contract.Contract;
import com.skep.contract.ContractRepository;
import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.DailyWorkLogRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.fieldDeployment.FieldDeploymentRepository;
import com.skep.fieldDeployment.FieldDeploymentRequest;
import com.skep.fieldDeployment.FieldDeploymentStatus;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.dispatch.DispatchedEquipment;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.quotation.dispatch.DispatchedPerson;
import com.skep.quotation.dispatch.DispatchedPersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.settlement.dto.SettlementDtos.OtBreakdown;
import com.skep.settlement.dto.SettlementDtos.OwnerSettlement;
import com.skep.settlement.dto.SettlementDtos.SettlementItem;
import com.skep.settlement.dto.SettlementDtos.SettlementSummaryResponse;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.workconfirmation.WorkConfirmation;
import com.skep.workconfirmation.WorkConfirmationRepository;
import com.skep.workconfirmation.WorkConfirmationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 소유자별 투입 정산 요약 (read-only).
 * 원천 = 배차 행(quotation_dispatched_equipments/persons)만. 소유자 = coalesce(sub_supplier_company_id, supplier_company_id).
 * 금액 = {@link SettlementCalculator} — 월대÷25×근무일수 + OT / 일대×근무일수 + OT. 근무일수·OT일수는 투입 관리 입력.
 * 현장 정산일(site.settlement_day)·work_period 는 표시용. 단가는 투입(배차) 시점 값.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final DispatchedEquipmentRepository dispatchedEquipment;
    private final DispatchedPersonRepository dispatchedPerson;
    private final QuotationRequestRepository requests;
    private final CompanyService companyService;
    private final CompanyRepository companies;
    private final EquipmentRepository equipments;
    private final PersonRepository persons;
    private final SiteRepository sites;
    private final WorkConfirmationRepository workConfirmations;
    private final DailyWorkLogRepository dailyWorkLogs;
    private final ContractRepository contracts;
    private final FieldDeploymentRepository fieldDeployments;

    @Transactional(readOnly = true)
    public SettlementSummaryResponse summary(AuthenticatedUser actor, Long companyIdParam, LocalDate from, LocalDate to) {
        Long companyId;
        if (actor.role() == Role.ADMIN) {
            if (companyIdParam == null) {
                throw ApiException.badRequest("COMPANY_REQUIRED", "companyId 파라미터가 필요합니다 (ADMIN)");
            }
            companyId = companyIdParam;
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "회사 미지정");
            companyId = actor.companyId();
        } else {
            throw ApiException.forbidden("SETTLEMENT_DENIED", "공급사/관리자만 조회할 수 있습니다");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_RANGE", "from 이 to 보다 늦습니다");
        }

        List<Long> scope = companyService.selfAndChildren(companyId);
        List<DispatchedEquipment> eqRows = dispatchedEquipment.findAllVisibleForSupplier(scope, companyId);
        List<DispatchedPerson> ppRows = dispatchedPerson.findAllVisibleForSupplier(scope, companyId);

        // 견적 요청 → 기간/사이트/BP.
        Set<Long> qrIds = new HashSet<>();
        eqRows.forEach(d -> qrIds.add(d.getQuotationRequestId()));
        ppRows.forEach(d -> qrIds.add(d.getQuotationRequestId()));
        Map<Long, QuotationRequest> qrMap = requests.findAllById(qrIds).stream()
                .collect(Collectors.toMap(QuotationRequest::getId, q -> q));

        // 날짜범위 조회: 견적 계약기간이 [from,to] 와 겹치는 배차행만 (미지정이면 전체).
        if (from != null || to != null) {
            eqRows = eqRows.stream().filter(d -> overlaps(qrMap.get(d.getQuotationRequestId()), from, to)).toList();
            ppRows = ppRows.stream().filter(d -> overlaps(qrMap.get(d.getQuotationRequestId()), from, to)).toList();
        }

        // §3.2 디커플링: 수락된 현장 투입요청(ACTIVE/COMPLETED)을 정산 두 번째 원천으로 추가.
        // 기존 배차(견적) 원천 처리는 위에서 그대로 — 아래는 순수 추가분이라 기존 항목 출력 불변.
        List<FieldDeploymentRequest> deployments = fieldDeployments.findBySupplierCompanyIdInAndStatusIn(
                scope, List.of(FieldDeploymentStatus.ACTIVE, FieldDeploymentStatus.COMPLETED));
        if (from != null || to != null) {
            deployments = deployments.stream().filter(r -> deploymentInWindow(r, from, to)).toList();
        }

        // 자원 라벨 — 배차 + 투입요청 자원 통합 조회.
        Set<Long> allEquipmentIds = new HashSet<>();
        Set<Long> allPersonIds = new HashSet<>();
        eqRows.forEach(d -> { if (d.getEquipmentId() != null) allEquipmentIds.add(d.getEquipmentId()); });
        ppRows.forEach(d -> { if (d.getPersonId() != null) allPersonIds.add(d.getPersonId()); });
        for (FieldDeploymentRequest r : deployments) {
            if (r.getResourceType() == OwnerType.EQUIPMENT) allEquipmentIds.add(r.getResourceId());
            else if (r.getResourceType() == OwnerType.PERSON) allPersonIds.add(r.getResourceId());
        }
        // R4 표시 전용 — 조합 장비 라벨 로드용. 라벨 맵(eqMap)에만 합류, OT 집계 대상(allEquipmentIds)은 불변.
        Set<Long> labelEquipmentIds = new HashSet<>(allEquipmentIds);
        ppRows.forEach(d -> { if (d.getComboEquipmentId() != null) labelEquipmentIds.add(d.getComboEquipmentId()); });
        deployments.forEach(r -> { if (r.getComboEquipmentId() != null) labelEquipmentIds.add(r.getComboEquipmentId()); });
        Map<Long, Equipment> eqMap = equipments.findAllById(labelEquipmentIds)
                .stream().collect(Collectors.toMap(Equipment::getId, e -> e));
        Map<Long, Person> personMap = persons.findAllById(allPersonIds)
                .stream().collect(Collectors.toMap(Person::getId, p -> p));

        // §3.6.3 OT 5분류 — 자원별 일일 확인서(SIGNED/PHOTO)×계약 단가 내역(기간 [from,to]).
        Map<String, OtBreakdown> otBreakdowns = computeOtBreakdowns(allEquipmentIds, allPersonIds, from, to);

        // 정산 근무일수 자동 파생 소스: 대상 인원들의 서명완료(COMPLETED) 작업확인서를
        // 인력 배차행 계약기간 전체 범위(min~max)로 한 번에 배치 조회. 행별 필터는 personItem 에서.
        Map<Long, List<WorkConfirmation>> completedByPerson = fetchCompletedConfirmations(ppRows, qrMap);

        // 사이트 이름 + 현장 정산일.
        Set<Long> siteIds = qrMap.values().stream().map(QuotationRequest::getSiteId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        deployments.forEach(r -> { if (r.getTargetSiteId() != null) siteIds.add(r.getTargetSiteId()); });
        List<Site> siteList = sites.findAllById(siteIds);
        Map<Long, String> siteNames = siteList.stream().collect(Collectors.toMap(Site::getId, Site::getName));
        Map<Long, Integer> siteSettlementDays = siteList.stream()
                .filter(s -> s.getSettlementDay() != null)
                .collect(Collectors.toMap(Site::getId, Site::getSettlementDay));

        // 회사 이름 (소유자 + BP).
        Set<Long> companyIds = new HashSet<>();
        for (DispatchedEquipment d : eqRows) companyIds.add(ownerOf(d.getSubSupplierCompanyId(), d.getSupplierCompanyId()));
        for (DispatchedPerson d : ppRows) companyIds.add(ownerOf(d.getSubSupplierCompanyId(), d.getSupplierCompanyId()));
        qrMap.values().forEach(q -> { Long bp = bpOf(q); if (bp != null) companyIds.add(bp); });
        for (FieldDeploymentRequest r : deployments) {
            companyIds.add(r.getSupplierCompanyId());
            if (r.getBpCompanyId() != null) companyIds.add(r.getBpCompanyId());
        }
        Map<Long, String> companyNames = companies.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));

        // 소유자별 그룹핑.
        Map<Long, List<SettlementItem>> byOwner = new LinkedHashMap<>();
        for (DispatchedEquipment d : eqRows) {
            Long ownerId = ownerOf(d.getSubSupplierCompanyId(), d.getSupplierCompanyId());
            byOwner.computeIfAbsent(ownerId, k -> new ArrayList<>())
                    .add(equipmentItem(d, qrMap, eqMap, siteNames, siteSettlementDays, companyNames, companyId, otBreakdowns));
        }
        for (DispatchedPerson d : ppRows) {
            Long ownerId = ownerOf(d.getSubSupplierCompanyId(), d.getSupplierCompanyId());
            byOwner.computeIfAbsent(ownerId, k -> new ArrayList<>())
                    .add(personItem(d, qrMap, personMap, eqMap, siteNames, siteSettlementDays, companyNames, companyId, completedByPerson, otBreakdowns));
        }
        // 디커플링 원천(투입요청 수락분) — 소유자 = supplierCompanyId(투입요청엔 하위귀속 개념 없음).
        for (FieldDeploymentRequest r : deployments) {
            byOwner.computeIfAbsent(r.getSupplierCompanyId(), k -> new ArrayList<>())
                    .add(fieldDeploymentItem(r, eqMap, personMap, siteNames, siteSettlementDays, companyNames, otBreakdowns));
        }

        List<OwnerSettlement> owners = new ArrayList<>();
        for (var entry : byOwner.entrySet()) {
            Long ownerId = entry.getKey();
            List<SettlementItem> its = entry.getValue().stream()
                    .sorted(Comparator.comparing(SettlementItem::sentAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .toList();
            long total = its.stream().map(SettlementItem::amount).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
            owners.add(new OwnerSettlement(ownerId, companyNames.getOrDefault(ownerId, "#" + ownerId),
                    ownerId.equals(companyId), its, total, its.size()));
        }
        // 본인 먼저, 그 다음 이름순.
        owners.sort(Comparator.comparing((OwnerSettlement o) -> o.isSelf() ? 0 : 1)
                .thenComparing(OwnerSettlement::ownerCompanyName));
        long grandTotal = owners.stream().mapToLong(OwnerSettlement::totalAmount).sum();
        return new SettlementSummaryResponse(owners, grandTotal);
    }

    private SettlementItem equipmentItem(DispatchedEquipment d, Map<Long, QuotationRequest> qrMap,
                                         Map<Long, Equipment> eqMap, Map<Long, String> siteNames,
                                         Map<Long, Integer> siteSettlementDays,
                                         Map<Long, String> companyNames, Long selfId,
                                         Map<String, OtBreakdown> otBreakdowns) {
        Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
        String label = e != null
                ? (e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "#" + e.getId()))
                : "단가 응답";
        QuotationRequest qr = qrMap.get(d.getQuotationRequestId());
        long periodDays = periodDays(qr);
        SettlementCalculator.Result p = SettlementCalculator.calc(
                d.getMonthlyPrice(), d.getDailyPrice(), d.getOtMonthlyPrice(), d.getOtDailyPrice(),
                d.getSettlementWorkDays(), d.getSettlementOtDays());
        Long bp = qr != null ? bpOf(qr) : null;
        Integer siteDay = qr != null && qr.getSiteId() != null ? siteSettlementDays.get(qr.getSiteId()) : null;
        return new SettlementItem(
                "EQUIPMENT", d.getId(), d.getEquipmentId(), label,
                d.getQuotationRequestId(),
                qr != null ? qr.getSiteId() : null,
                qr != null && qr.getSiteId() != null ? siteNames.get(qr.getSiteId()) : null,
                bp, bp != null ? companyNames.get(bp) : null,
                qr != null ? qr.getWorkPeriodStart() : null,
                qr != null ? qr.getWorkPeriodEnd() : null,
                periodDays,
                d.getDailyPrice(), d.getOtDailyPrice(), d.getMonthlyPrice(), d.getOtMonthlyPrice(),
                p.basis(), p.amount(),
                d.getSupplierCompanyId(),
                d.getSubSupplierCompanyId() != null && d.getSubSupplierCompanyId().equals(selfId),
                d.getSentAt(),
                d.getSettlementWorkDays(), d.getSettlementOtDays(), p.baseAmount(), p.otAmount(), siteDay,
                // 장비는 파생 소스 없음(무변경). source 는 수동 입력 여부만 표기.
                null, null, d.getSettlementWorkDays() != null ? "MANUAL" : null,
                "DISPATCH", d.getEquipmentId() != null ? otBreakdowns.get("EQUIPMENT:" + d.getEquipmentId()) : null,
                // R4: 배차 장비 행엔 저장 조합 없음(조종원 행이 장비를 가리키는 방향) — 그룹핑은 조종원 행 combo 로.
                null, null);
    }

    private SettlementItem personItem(DispatchedPerson d, Map<Long, QuotationRequest> qrMap,
                                      Map<Long, Person> personMap, Map<Long, Equipment> eqMap,
                                      Map<Long, String> siteNames,
                                      Map<Long, Integer> siteSettlementDays,
                                      Map<Long, String> companyNames, Long selfId,
                                      Map<Long, List<WorkConfirmation>> completedByPerson,
                                      Map<String, OtBreakdown> otBreakdowns) {
        Person p = personMap.get(d.getPersonId());
        String label = p != null ? p.getName() : "#" + d.getPersonId();
        QuotationRequest qr = qrMap.get(d.getQuotationRequestId());
        long periodDays = periodDays(qr);

        // 자동 파생: 이 인원의 서명완료 확인서 중 계약기간 [start..end] 내 distinct workDate 수(+ OT일수).
        LocalDate ps = qr != null ? qr.getWorkPeriodStart() : null;
        LocalDate pe = qr != null ? qr.getWorkPeriodEnd() : null;
        int[] der = deriveWorkDays(completedByPerson.get(d.getPersonId()), ps, pe);
        Integer derivedWorkDays = der[0] > 0 ? der[0] : null;
        Integer derivedOtDays = der[0] > 0 ? der[1] : null;

        // 수동 우선: settlementWorkDays 가 있으면 그대로(기존 동작), 없을 때만 파생값 사용.
        boolean manual = d.getSettlementWorkDays() != null;
        Integer effWorkDays = manual ? d.getSettlementWorkDays() : derivedWorkDays;
        Integer effOtDays = manual ? d.getSettlementOtDays() : derivedOtDays;
        String source = manual ? "MANUAL" : (derivedWorkDays != null ? "DERIVED" : null);

        // 인력 배차행의 OT 단가로 OT 금액 산출. OT 단가 NULL(기존 행)이면 calc 가 OT=0 → 정산 불변.
        SettlementCalculator.Result pr = SettlementCalculator.calc(
                d.getMonthlyPrice(), d.getDailyPrice(), d.getOtMonthlyPrice(), d.getOtDailyPrice(),
                effWorkDays, effOtDays);
        Long bp = qr != null ? bpOf(qr) : null;
        Integer siteDay = qr != null && qr.getSiteId() != null ? siteSettlementDays.get(qr.getSiteId()) : null;
        return new SettlementItem(
                "PERSON", d.getId(), d.getPersonId(), label,
                d.getQuotationRequestId(),
                qr != null ? qr.getSiteId() : null,
                qr != null && qr.getSiteId() != null ? siteNames.get(qr.getSiteId()) : null,
                bp, bp != null ? companyNames.get(bp) : null,
                qr != null ? qr.getWorkPeriodStart() : null,
                qr != null ? qr.getWorkPeriodEnd() : null,
                periodDays,
                d.getDailyPrice(), d.getOtDailyPrice(), d.getMonthlyPrice(), d.getOtMonthlyPrice(),
                pr.basis(), pr.amount(),
                d.getSupplierCompanyId(),
                d.getSubSupplierCompanyId() != null && d.getSubSupplierCompanyId().equals(selfId),
                d.getSentAt(),
                d.getSettlementWorkDays(), d.getSettlementOtDays(), pr.baseAmount(), pr.otAmount(), siteDay,
                derivedWorkDays, derivedOtDays, source,
                "DISPATCH", d.getPersonId() != null ? otBreakdowns.get("PERSON:" + d.getPersonId()) : null,
                d.getComboEquipmentId(), comboLabel(d.getComboEquipmentId(), eqMap));
    }

    /** §3.2 디커플링 — 수락된 투입요청 1건 = 정산 1건(DEPLOYMENT). 견적 무관, 근무일수 미입력 → 금액 basis 만. */
    private SettlementItem fieldDeploymentItem(FieldDeploymentRequest r,
                                               Map<Long, Equipment> eqMap, Map<Long, Person> personMap,
                                               Map<Long, String> siteNames, Map<Long, Integer> siteSettlementDays,
                                               Map<Long, String> companyNames, Map<String, OtBreakdown> otBreakdowns) {
        boolean isEq = r.getResourceType() == OwnerType.EQUIPMENT;
        String type = isEq ? "EQUIPMENT" : "PERSON";
        Long resourceId = r.getResourceId();
        String label;
        if (isEq) {
            Equipment e = eqMap.get(resourceId);
            label = e != null
                    ? (e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "#" + resourceId))
                    : "#" + resourceId;
        } else {
            Person p = personMap.get(resourceId);
            label = p != null ? p.getName() : "#" + resourceId;
        }
        // 근무일수 미입력 → SettlementCalculator 는 basis 만(amount null). 기존 배차행과 동일 규칙.
        SettlementCalculator.Result calc = SettlementCalculator.calc(
                r.getMonthlyPrice(), r.getDailyPrice(), null, null, null, null);
        Long siteId = r.getTargetSiteId();
        Long bp = r.getBpCompanyId();
        return new SettlementItem(
                type, r.getId(), resourceId, label,
                null,                               // quotationRequestId — 없음(디커플)
                siteId, siteId != null ? siteNames.get(siteId) : null,
                bp, bp != null ? companyNames.get(bp) : null,
                r.getStartDate(), null,             // 시작일만(투입요청엔 종료일 없음)
                0,
                r.getDailyPrice(), null, r.getMonthlyPrice(), null,
                calc.basis(), calc.amount(),
                r.getSupplierCompanyId(),
                false,
                r.getRequestedAt(),
                null, null, calc.baseAmount(), calc.otAmount(),
                siteId != null ? siteSettlementDays.get(siteId) : null,
                null, null, null,
                "DEPLOYMENT", otBreakdowns.get(type + ":" + resourceId),
                r.getComboEquipmentId(), comboLabel(r.getComboEquipmentId(), eqMap));
    }

    /** 자원별 OT 5분류 breakdown 산출 — 일일 확인서를 기본 자원(장비 우선)에 1회 귀속(이중집계 방지). */
    private Map<String, OtBreakdown> computeOtBreakdowns(Set<Long> equipmentIds, Set<Long> personIds,
                                                         LocalDate from, LocalDate to) {
        Map<String, List<DailyWorkLog>> byResource = new HashMap<>();
        if (!equipmentIds.isEmpty()) {
            for (DailyWorkLog l : dailyWorkLogs.findByEquipmentIdIn(equipmentIds)) {
                if (l.getEquipmentId() == null || !logInWindow(l, from, to)) continue;
                byResource.computeIfAbsent("EQUIPMENT:" + l.getEquipmentId(), k -> new ArrayList<>()).add(l);
            }
        }
        if (!personIds.isEmpty()) {
            for (DailyWorkLog l : dailyWorkLogs.findByPersonIdIn(personIds)) {
                if (l.getEquipmentId() != null) continue; // 장비 로그는 이미 장비에 귀속(이중집계 방지).
                if (l.getPersonId() == null || !logInWindow(l, from, to)) continue;
                byResource.computeIfAbsent("PERSON:" + l.getPersonId(), k -> new ArrayList<>()).add(l);
            }
        }
        if (byResource.isEmpty()) return Map.of();
        Set<Long> contractIds = new HashSet<>();
        byResource.values().forEach(list -> list.forEach(l -> {
            if (l.getContractId() != null) contractIds.add(l.getContractId());
        }));
        Map<Long, Contract> contractMap = contracts.findAllById(contractIds).stream()
                .collect(Collectors.toMap(Contract::getId, c -> c));
        Map<String, OtBreakdown> out = new HashMap<>();
        for (var e : byResource.entrySet()) {
            OtBreakdown b = OtBreakdownCalculator.compute(e.getValue(), contractMap);
            if (b != null) out.put(e.getKey(), b);
        }
        return out;
    }

    private static boolean logInWindow(DailyWorkLog l, LocalDate from, LocalDate to) {
        LocalDate d = l.getWorkDate();
        if (d == null) return false;
        if (from != null && d.isBefore(from)) return false;
        if (to != null && d.isAfter(to)) return false;
        return true;
    }

    /** 투입요청이 [from,to] 와 겹치나 — 시작일 기준(종료일 없어 from 하한 미적용, 진행중일 수 있음). */
    private static boolean deploymentInWindow(FieldDeploymentRequest r, LocalDate from, LocalDate to) {
        LocalDate s = r.getStartDate();
        if (s == null) return true;
        return to == null || !s.isAfter(to);
    }

    /** 소유자 = 자식 귀속이 있으면 자식, 없으면 대외 명의(supplier). */
    private static Long ownerOf(Long subSupplierCompanyId, Long supplierCompanyId) {
        return subSupplierCompanyId != null ? subSupplierCompanyId : supplierCompanyId;
    }

    /** R4 표시 전용 — 조합 장비 라벨(차량번호>모델>#id). 비조합(null)이면 null. */
    private static String comboLabel(Long comboEquipmentId, Map<Long, Equipment> eqMap) {
        if (comboEquipmentId == null) return null;
        Equipment e = eqMap.get(comboEquipmentId);
        if (e == null) return "#" + comboEquipmentId;
        return e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "#" + e.getId());
    }

    private static Long bpOf(QuotationRequest qr) {
        return qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
    }

    private static long periodDays(QuotationRequest qr) {
        if (qr == null || qr.getWorkPeriodStart() == null || qr.getWorkPeriodEnd() == null) return 0;
        return ChronoUnit.DAYS.between(qr.getWorkPeriodStart(), qr.getWorkPeriodEnd()) + 1;
    }

    /** 견적 계약기간이 [from,to] 와 겹치나. 한쪽 null 이면 무한대. 기간 없으면 제외. */
    private static boolean overlaps(QuotationRequest qr, LocalDate from, LocalDate to) {
        if (qr == null || qr.getWorkPeriodStart() == null || qr.getWorkPeriodEnd() == null) return false;
        if (from != null && qr.getWorkPeriodEnd().isBefore(from)) return false;
        if (to != null && qr.getWorkPeriodStart().isAfter(to)) return false;
        return true;
    }

    /** 인력 배차행들의 계약기간 전체 범위(min~max)로 대상 인원 서명완료 확인서를 한 번에 조회 → person별 그룹. */
    private Map<Long, List<WorkConfirmation>> fetchCompletedConfirmations(
            List<DispatchedPerson> ppRows, Map<Long, QuotationRequest> qrMap) {
        LocalDate winStart = null, winEnd = null;
        Set<Long> personIds = new HashSet<>();
        for (DispatchedPerson d : ppRows) {
            if (d.getPersonId() != null) personIds.add(d.getPersonId());
            QuotationRequest qr = qrMap.get(d.getQuotationRequestId());
            if (qr == null) continue;
            if (qr.getWorkPeriodStart() != null && (winStart == null || qr.getWorkPeriodStart().isBefore(winStart))) winStart = qr.getWorkPeriodStart();
            if (qr.getWorkPeriodEnd() != null && (winEnd == null || qr.getWorkPeriodEnd().isAfter(winEnd))) winEnd = qr.getWorkPeriodEnd();
        }
        Map<Long, List<WorkConfirmation>> byPerson = new HashMap<>();
        if (personIds.isEmpty() || winStart == null || winEnd == null) return byPerson;
        for (WorkConfirmation wc : workConfirmations.findByPersonIdInAndWorkDateBetween(personIds, winStart, winEnd)) {
            if (wc.getStatus() != WorkConfirmationStatus.COMPLETED) continue;
            byPerson.computeIfAbsent(wc.getPersonId(), k -> new ArrayList<>()).add(wc);
        }
        return byPerson;
    }

    /** 확인서(서명완료) 중 [start..end] 내 distinct workDate 수, 그중 overtime_hours>0 인 날 수. 파생 불가면 {0,0}. */
    private static int[] deriveWorkDays(List<WorkConfirmation> completed, LocalDate start, LocalDate end) {
        if (completed == null || start == null || end == null) return new int[]{0, 0};
        Set<LocalDate> days = new HashSet<>();
        Set<LocalDate> otDays = new HashSet<>();
        for (WorkConfirmation wc : completed) {
            LocalDate d = wc.getWorkDate();
            if (d == null || d.isBefore(start) || d.isAfter(end)) continue;
            days.add(d);
            if (wc.getOvertimeHours() != null && wc.getOvertimeHours().signum() > 0) otDays.add(d);
        }
        return new int[]{days.size(), otDays.size()};
    }

    /** 투입 관리 — 배차행에 정산용 근무일수·OT일수 입력. 소유자(본인/협력사) 범위만. */
    @Transactional
    public void setQuantity(AuthenticatedUser actor, String type, Long dispatchId, Integer workDays, Integer otDays) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SETTLEMENT_DENIED", "공급사/관리자만 입력할 수 있습니다");
        }
        if (workDays != null && workDays < 0) throw ApiException.badRequest("INVALID_DAYS", "근무일수는 0 이상이어야 합니다");
        if (otDays != null && otDays < 0) throw ApiException.badRequest("INVALID_DAYS", "OT일수는 0 이상이어야 합니다");
        List<Long> scope = actor.role() == Role.ADMIN ? null : companyService.selfAndChildren(actor.companyId());
        if ("EQUIPMENT".equalsIgnoreCase(type)) {
            DispatchedEquipment d = dispatchedEquipment.findById(dispatchId)
                    .orElseThrow(() -> ApiException.notFound("DISPATCH_NOT_FOUND", "배차 " + dispatchId + " 없음"));
            ensureOwner(scope, d.getSupplierCompanyId(), d.getSubSupplierCompanyId());
            d.applySettlementQuantity(workDays, otDays);
            dispatchedEquipment.save(d);
        } else if ("PERSON".equalsIgnoreCase(type)) {
            DispatchedPerson d = dispatchedPerson.findById(dispatchId)
                    .orElseThrow(() -> ApiException.notFound("DISPATCH_NOT_FOUND", "배차 " + dispatchId + " 없음"));
            ensureOwner(scope, d.getSupplierCompanyId(), d.getSubSupplierCompanyId());
            d.applySettlementQuantity(workDays, otDays);
            dispatchedPerson.save(d);
        } else {
            throw ApiException.badRequest("INVALID_TYPE", "type 은 EQUIPMENT 또는 PERSON");
        }
    }

    private static void ensureOwner(List<Long> scope, Long supplierCompanyId, Long subSupplierCompanyId) {
        if (scope == null) return; // ADMIN
        if (scope.contains(supplierCompanyId) || (subSupplierCompanyId != null && scope.contains(subSupplierCompanyId))) return;
        throw ApiException.forbidden("NOT_OWNER", "본인/협력사 배차만 입력할 수 있습니다");
    }
}
