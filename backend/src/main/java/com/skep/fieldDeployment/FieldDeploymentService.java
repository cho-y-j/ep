package com.skep.fieldDeployment;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.fieldDeployment.dto.CreateComboFieldDeploymentRequest;
import com.skep.fieldDeployment.dto.CreateFieldDeploymentRequest;
import com.skep.fieldDeployment.dto.FieldDeploymentBoardItem;
import com.skep.fieldDeployment.dto.FieldDeploymentResponse;
import com.skep.fieldDeployment.dto.ReviewFieldDeploymentRequest;
import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.workconfirmation.WorkConfirmation;
import com.skep.workconfirmation.WorkConfirmationRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanEquipmentRepository;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import java.time.LocalDate;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldDeploymentService {

    private final FieldDeploymentRepository repo;
    private final CompanyRepository companies;
    private final CompanyService companyService;
    private final EquipmentRepository equipments;
    private final PersonRepository persons;
    private final SiteRepository sites;
    private final NotificationService notifications;
    private final WorkConfirmationRepository workConfirmationRepo;
    private final WorkPlanRepository workPlanRepo;
    private final WorkPlanEquipmentRepository wpeRepo;
    private final WorkPlanPersonRepository wppRepo;
    private final AttendanceSessionRepository attendanceSessions;

    /** 공급사가 자기 자원을 BP 에 투입 요청. */
    public FieldDeploymentResponse create(CreateFieldDeploymentRequest req, AuthenticatedUser actor) {
        Long supplierId = requireSupplierActor(actor);
        requireOwnResource(req.resourceType(), req.resourceId(), supplierId, actor);

        FieldDeploymentRequest row = FieldDeploymentRequest.builder()
                .supplierCompanyId(supplierId)
                .bpCompanyId(req.bpCompanyId())
                .resourceType(req.resourceType())
                .resourceId(req.resourceId())
                .targetSiteId(req.targetSiteId())
                .startDate(req.startDate())
                .note(req.note())
                .dailyPrice(req.dailyPrice())
                .monthlyPrice(req.monthlyPrice())
                .otPrice(req.otPrice())
                .nightPrice(req.nightPrice())
                .requestedByUserId(actor.id())
                .build();
        repo.save(row);

        notifications.sendToCompany(req.bpCompanyId(),
                NotificationType.FIELD_DEPLOYMENT_REQUESTED,
                "현장 투입 요청 도착",
                resourceLabel(row.getResourceType(), row.getResourceId()) + " 투입 요청",
                "FIELD_DEPLOYMENT", row.getId(), req.targetSiteId(), notifications.senderLabelOf(actor));
        return toResponse(row);
    }

    /**
     * R3: 조합(장비+교대조 조종원) 투입 요청 — 단일 트랜잭션으로 장비 1행 + 조종원 N행 생성(단가는 행 단위).
     * 전 행에 combo_equipment_id 스냅샷. 가드는 단건 create 와 공용(requireOwnResource) —
     * 자원 1건이라도 스코프 밖이면 403 전체 롤백. BP 수신 알림은 행당 N건 대신 묶음 1건.
     */
    public List<FieldDeploymentResponse> createCombo(CreateComboFieldDeploymentRequest req, AuthenticatedUser actor) {
        Long supplierId = requireSupplierActor(actor);
        List<Long> operatorIds = req.operatorPersonIds() == null ? List.of()
                : req.operatorPersonIds().stream().distinct().toList();

        requireOwnResource(OwnerType.EQUIPMENT, req.equipmentId(), supplierId, actor);
        for (Long pid : operatorIds) requireOwnResource(OwnerType.PERSON, pid, supplierId, actor);

        Map<Long, CreateComboFieldDeploymentRequest.OperatorPrice> priceByPerson = new HashMap<>();
        if (req.operatorPrices() != null) {
            for (var op : req.operatorPrices()) priceByPerson.put(op.personId(), op);
        }

        List<FieldDeploymentRequest> rows = new java.util.ArrayList<>();
        var ep = req.equipmentPrices();
        rows.add(comboRow(supplierId, req, OwnerType.EQUIPMENT, req.equipmentId(),
                ep != null ? ep.dailyPrice() : null, ep != null ? ep.monthlyPrice() : null,
                ep != null ? ep.otPrice() : null, ep != null ? ep.nightPrice() : null, actor.id()));
        for (Long pid : operatorIds) {
            var op = priceByPerson.get(pid);
            rows.add(comboRow(supplierId, req, OwnerType.PERSON, pid,
                    op != null ? op.dailyPrice() : null, op != null ? op.monthlyPrice() : null,
                    op != null ? op.otPrice() : null, op != null ? op.nightPrice() : null, actor.id()));
        }

        // BP 알림 — 묶음 1건: 『차량번호』 조합 투입 요청 N건. 링크는 첫 행(수신함 진입용).
        String comboLabel = resourceLabel(OwnerType.EQUIPMENT, req.equipmentId());
        String operatorNames = persons.findAllById(operatorIds).stream()
                .map(Person::getName).collect(java.util.stream.Collectors.joining(", "));
        notifications.sendToCompany(req.bpCompanyId(),
                NotificationType.FIELD_DEPLOYMENT_REQUESTED,
                "『" + comboLabel + "』 조합 투입 요청 " + rows.size() + "건",
                (operatorNames.isBlank() ? "" : "조종원: " + operatorNames + " — ")
                        + "장비+조종원 조합 투입 요청",
                "FIELD_DEPLOYMENT", rows.get(0).getId(), req.targetSiteId(), notifications.senderLabelOf(actor));
        return rows.stream().map(this::toResponse).toList();
    }

    /** 조합 행 생성 — 공통 필드는 요청에서, 단가만 행 단위. 전 행 combo_equipment_id=equipment_id 스냅샷. */
    private FieldDeploymentRequest comboRow(Long supplierId, CreateComboFieldDeploymentRequest req,
                                            OwnerType type, Long resourceId,
                                            Long daily, Long monthly, Long ot, Long night, Long actorId) {
        return repo.save(FieldDeploymentRequest.builder()
                .supplierCompanyId(supplierId)
                .bpCompanyId(req.bpCompanyId())
                .resourceType(type)
                .resourceId(resourceId)
                .targetSiteId(req.targetSiteId())
                .startDate(req.startDate())
                .note(req.note())
                .dailyPrice(daily)
                .monthlyPrice(monthly)
                .otPrice(ot)
                .nightPrice(night)
                .comboEquipmentId(req.equipmentId())
                .requestedByUserId(actorId)
                .build());
    }

    /** 단건(create)·조합(createCombo) 공용 — 공급사 역할 + 회사 식별 가드. */
    private Long requireSupplierActor(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER
                && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 요청 가능");
        }
        Long supplierId = actor.companyId();
        if (supplierId == null) throw ApiException.forbidden("NO_COMPANY", "회사 정보 없음");
        return supplierId;
    }

    /**
     * 단건(create)·조합(createCombo) 공용 — 본인 회사(+직속 자식 협력사) 소유 자원 가드(ADMIN 예외).
     * V77 부모→자식 단방향: 수집·검사·심사와 동일하게 selfAndChildren 스코프 — 형제/무관사는 403 유지.
     */
    private void requireOwnResource(OwnerType resourceType, Long resourceId, Long supplierId, AuthenticatedUser actor) {
        Long ownerCompanyId;
        if (resourceType == OwnerType.EQUIPMENT) {
            ownerCompanyId = equipments.findById(resourceId).orElseThrow(() ->
                    ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 없음")).getSupplierId();
        } else if (resourceType == OwnerType.PERSON) {
            ownerCompanyId = persons.findById(resourceId).orElseThrow(() ->
                    ApiException.notFound("PERSON_NOT_FOUND", "인원 없음")).getSupplierId();
        } else {
            throw ApiException.badRequest("BAD_TYPE", "장비/인원만 가능");
        }
        if (actor.role() != Role.ADMIN && !companyService.selfAndChildren(supplierId).contains(ownerCompanyId)) {
            throw ApiException.forbidden("DENIED", "본인 회사(협력사 포함) 자원만 요청 가능");
        }
    }

    public List<FieldDeploymentResponse> listForSupplier(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return repo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream()
                .map(this::toResponse).toList();
    }

    public List<FieldDeploymentResponse> listForBp(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return repo.findByBpCompanyIdOrderByIdDesc(actor.companyId()).stream()
                .map(this::toResponse).toList();
    }

    public FieldDeploymentResponse accept(Long id, String note, Long overrideSiteId, AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ONLY", "BP만 수락 가능");
        }
        FieldDeploymentRequest row = getOrThrow(id);
        if (actor.role() == Role.BP && !row.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사 요청만 처리");
        }
        if (row.getStatus() != FieldDeploymentStatus.REQUESTED) {
            throw ApiException.badRequest("INVALID_STATE", "REQUESTED 상태에서만 수락 가능");
        }
        row.accept(actor.id(), note, overrideSiteId);
        notifications.sendToCompany(row.getSupplierCompanyId(),
                NotificationType.FIELD_DEPLOYMENT_REVIEWED,
                "투입 요청 수락",
                resourceLabel(row.getResourceType(), row.getResourceId()) + " 투입 수락 — 현장 운영 시작",
                "FIELD_DEPLOYMENT", row.getId(), row.getTargetSiteId(), notifications.senderLabelOf(actor));
        return toResponse(row);
    }

    /**
     * R3: 조합 일괄 수락 — 전건 REQUESTED + 같은 combo + 자기 수신분 검증(1건이라도 위반 시 예외 → 전체 롤백).
     * 통과 후 단건 accept 재사용(루프, 단일 트랜잭션). 부분 처리는 기존 단건 API 로.
     */
    public List<FieldDeploymentResponse> acceptCombo(List<Long> requestIds, String note, Long overrideSiteId,
                                                     AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ONLY", "BP만 수락 가능");
        }
        if (requestIds == null || requestIds.isEmpty()) {
            throw ApiException.badRequest("NO_REQUESTS", "수락할 요청이 없습니다");
        }
        List<Long> ids = requestIds.stream().distinct().toList();
        List<FieldDeploymentRequest> rows = ids.stream().map(this::getOrThrow).toList();
        Long comboId = rows.get(0).getComboEquipmentId();
        if (comboId == null) {
            throw ApiException.badRequest("NOT_COMBO", "조합 요청 건이 아닙니다");
        }
        for (FieldDeploymentRequest row : rows) {
            if (!comboId.equals(row.getComboEquipmentId())) {
                throw ApiException.badRequest("COMBO_MISMATCH", "같은 조합의 요청만 일괄 수락 가능");
            }
            if (actor.role() == Role.BP && !row.getBpCompanyId().equals(actor.companyId())) {
                throw ApiException.forbidden("DENIED", "본인 회사 요청만 처리");
            }
            if (row.getStatus() != FieldDeploymentStatus.REQUESTED) {
                throw ApiException.badRequest("INVALID_STATE",
                        "이미 처리된 건이 포함되어 있습니다 — 남은 건은 개별 수락하세요");
            }
        }
        return ids.stream().map(id -> accept(id, note, overrideSiteId, actor)).toList();
    }

    public FieldDeploymentResponse reject(Long id, ReviewFieldDeploymentRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ONLY", "BP만 반려 가능");
        }
        FieldDeploymentRequest row = getOrThrow(id);
        if (actor.role() == Role.BP && !row.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사 요청만 처리");
        }
        if (row.getStatus() != FieldDeploymentStatus.REQUESTED) {
            throw ApiException.badRequest("INVALID_STATE", "REQUESTED 상태에서만 반려 가능");
        }
        row.reject(actor.id(), req != null ? req.note() : null);
        notifications.sendToCompany(row.getSupplierCompanyId(),
                NotificationType.FIELD_DEPLOYMENT_REVIEWED,
                "투입 요청 반려",
                resourceLabel(row.getResourceType(), row.getResourceId()) + " 투입 반려",
                "FIELD_DEPLOYMENT", row.getId(), row.getTargetSiteId(), notifications.senderLabelOf(actor));
        return toResponse(row);
    }

    public FieldDeploymentResponse cancel(Long id, AuthenticatedUser actor) {
        FieldDeploymentRequest row = getOrThrow(id);
        if (actor.role() != Role.ADMIN && !row.getSupplierCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "요청자만 취소 가능");
        }
        if (row.getStatus() != FieldDeploymentStatus.REQUESTED) {
            throw ApiException.badRequest("INVALID_STATE", "REQUESTED 상태에서만 취소 가능");
        }
        row.cancel();
        return toResponse(row);
    }

    public FieldDeploymentResponse complete(Long id, AuthenticatedUser actor) {
        FieldDeploymentRequest row = getOrThrow(id);
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ONLY", "BP만 종료 가능");
        }
        if (actor.role() == Role.BP && !row.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사 요청만 처리");
        }
        if (row.getStatus() != FieldDeploymentStatus.ACTIVE) {
            throw ApiException.badRequest("INVALID_STATE", "ACTIVE 상태에서만 종료 가능");
        }
        row.complete();
        return toResponse(row);
    }

    /** BP 투입 현황 대시보드 — 사인 완료된 작업계획서(SUBMITTED 이상) 의 자원 단위 board. */
    @Transactional(readOnly = true)
    public List<FieldDeploymentBoardItem> activeBoard(AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ONLY", "BP/ADMIN 전용");
        }
        Long bpId = actor.companyId();
        if (bpId == null) return List.of();

        var activeStatuses = List.of(WorkPlanStatus.SUBMITTED, WorkPlanStatus.APPROVED,
                WorkPlanStatus.IN_PROGRESS, WorkPlanStatus.DONE);
        List<WorkPlan> wps = workPlanRepo.findByBpCompanyIdAndStatusInOrderByIdDesc(bpId, activeStatuses);
        if (wps.isEmpty()) return List.of();
        List<Long> wpIds = wps.stream().map(WorkPlan::getId).toList();
        Map<Long, WorkPlan> wpById = new HashMap<>();
        for (WorkPlan wp : wps) wpById.put(wp.getId(), wp);

        Map<Long, String> companyNames = new HashMap<>();
        Map<Long, Site> siteMap = new HashMap<>();

        Map<Long, List<WorkConfirmation>> wcByPerson = new HashMap<>();
        for (var wc : workConfirmationRepo.findByBpCompanyIdOrderByWorkDateDescIdDesc(bpId)) {
            wcByPerson.computeIfAbsent(wc.getPersonId(), k -> new java.util.ArrayList<>()).add(wc);
        }
        LocalDate today = LocalDate.now();

        // 자원 단위 unique — 같은 자원이 여러 wp 에 등장하면 가장 최근(큰 id) wp 의 정보 사용.
        Map<String, FieldDeploymentBoardItem> bestByResource = new java.util.LinkedHashMap<>();

        // 차량별 운전자(workPlanPerson.equipmentId == 이 장비) 매핑 — 차량 가동시간 = 운전자 작업확인서 시간 합산.
        Map<Long, java.util.Set<Long>> driversByEquipment = new HashMap<>();
        java.util.Set<Long> allPersonIds = new java.util.HashSet<>();
        for (WorkPlanPerson wppRow : wppRepo.findByWorkPlanIdIn(wpIds)) {
            if (wppRow.getEquipmentId() != null) {
                driversByEquipment.computeIfAbsent(wppRow.getEquipmentId(), k -> new java.util.HashSet<>()).add(wppRow.getPersonId());
            }
            allPersonIds.add(wppRow.getPersonId());
        }

        // 오늘 출근한 사람들 AttendanceSession → personId 키. (가장 최근 한 건씩)
        Map<Long, AttendanceSession> todaySessionByPerson = new HashMap<>();
        if (!allPersonIds.isEmpty()) {
            var startOfDay = today.atStartOfDay();
            for (AttendanceSession s : attendanceSessions
                    .findByPersonIdInAndCheckInAtGreaterThanEqualOrderByCheckInAtDesc(allPersonIds, startOfDay)) {
                todaySessionByPerson.putIfAbsent(s.getPersonId(), s);
            }
        }

        for (WorkPlanEquipment wpe : wpeRepo.findByWorkPlanIdIn(wpIds)) {
            WorkPlan wp = wpById.get(wpe.getWorkPlanId());
            if (wp == null) continue;
            String key = "EQUIPMENT:" + wpe.getEquipmentId();
            if (bestByResource.containsKey(key)) continue; // wpIds 가 id desc 이므로 첫 매치가 최신
            String label = resourceLabel(OwnerType.EQUIPMENT, wpe.getEquipmentId());
            String supName = companyNames.computeIfAbsent(wpe.getSupplierCompanyId(),
                    id -> companies.findById(id).map(Company::getName).orElse(null));
            Site site = wp.getSiteId() != null
                    ? siteMap.computeIfAbsent(wp.getSiteId(), id -> sites.findById(id).orElse(null))
                    : null;
            Equipment e = equipments.findById(wpe.getEquipmentId()).orElse(null);
            Boolean hasPhoto = e != null && e.getPhotoKey() != null;

            // 운전자들의 작업확인서를 차량 가동시간으로 집계
            var drivers = driversByEquipment.getOrDefault(wpe.getEquipmentId(), java.util.Set.of());
            List<WorkConfirmation> combinedWcs = new java.util.ArrayList<>();
            for (Long driverId : drivers) {
                combinedWcs.addAll(wcByPerson.getOrDefault(driverId, List.of()));
            }
            combinedWcs.sort((a, b) -> b.getWorkDate().compareTo(a.getWorkDate()));
            Integer totalDays = (int) combinedWcs.stream().map(WorkConfirmation::getWorkDate).distinct().count();
            java.math.BigDecimal totalHours = combinedWcs.stream()
                    .map(w -> w.getTotalHours() != null ? w.getTotalHours() : java.math.BigDecimal.ZERO)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            LocalDate lastDate = combinedWcs.stream().map(WorkConfirmation::getWorkDate)
                    .max(java.util.Comparator.naturalOrder()).orElse(null);
            Boolean todayAttended = combinedWcs.stream().anyMatch(w -> today.equals(w.getWorkDate()))
                    || drivers.stream().anyMatch(todaySessionByPerson::containsKey);
            List<FieldDeploymentBoardItem.RecentConfirmation> recent = combinedWcs.stream().limit(7).map(w ->
                    new FieldDeploymentBoardItem.RecentConfirmation(
                            w.getId(), w.getWorkDate(), w.getTotalHours(),
                            w.getMorningTime(), w.getAfternoonTime(),
                            w.getSupplierSignedAt() != null, w.getBpSignedAt() != null,
                            w.getAttendancePhotoDocId()
                    )).toList();

            AttendanceSession eqSession = drivers.stream()
                    .map(todaySessionByPerson::get).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            bestByResource.put(key, new FieldDeploymentBoardItem(
                    wpe.getId(), OwnerType.EQUIPMENT, wpe.getEquipmentId(), label,
                    hasPhoto, wp.getId(),
                    wpe.getSupplierCompanyId(), supName,
                    wp.getSiteId(), site != null ? site.getName() : null,
                    site != null ? site.getLatitude() : null,
                    site != null ? site.getLongitude() : null,
                    wp.getWorkDate(), null,
                    drivers.isEmpty() ? null : totalDays,
                    drivers.isEmpty() ? null : totalHours,
                    lastDate, drivers.isEmpty() ? null : todayAttended,
                    eqSession != null ? eqSession.getCheckInLat() : null,
                    eqSession != null ? eqSession.getCheckInLng() : null,
                    eqSession != null ? eqSession.getCheckOutLat() : null,
                    eqSession != null ? eqSession.getCheckOutLng() : null,
                    recent
            ));
        }

        for (WorkPlanPerson wpp : wppRepo.findByWorkPlanIdIn(wpIds)) {
            WorkPlan wp = wpById.get(wpp.getWorkPlanId());
            if (wp == null) continue;
            String key = "PERSON:" + wpp.getPersonId();
            if (bestByResource.containsKey(key)) continue;
            String label = resourceLabel(OwnerType.PERSON, wpp.getPersonId());
            String supName = companyNames.computeIfAbsent(wpp.getSupplierCompanyId(),
                    id -> companies.findById(id).map(Company::getName).orElse(null));
            Site site = wp.getSiteId() != null
                    ? siteMap.computeIfAbsent(wp.getSiteId(), id -> sites.findById(id).orElse(null))
                    : null;
            var wcs = wcByPerson.getOrDefault(wpp.getPersonId(), List.of());
            Integer totalDays = wcs.size();
            java.math.BigDecimal totalHours = wcs.stream()
                    .map(w -> w.getTotalHours() != null ? w.getTotalHours() : java.math.BigDecimal.ZERO)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            LocalDate lastDate = wcs.stream().map(WorkConfirmation::getWorkDate)
                    .max(java.util.Comparator.naturalOrder()).orElse(null);
            Boolean todayAttended = wcs.stream().anyMatch(w -> today.equals(w.getWorkDate()))
                    || todaySessionByPerson.containsKey(wpp.getPersonId());
            List<FieldDeploymentBoardItem.RecentConfirmation> recent = wcs.stream().limit(7).map(w ->
                    new FieldDeploymentBoardItem.RecentConfirmation(
                            w.getId(), w.getWorkDate(), w.getTotalHours(),
                            w.getMorningTime(), w.getAfternoonTime(),
                            w.getSupplierSignedAt() != null, w.getBpSignedAt() != null,
                            w.getAttendancePhotoDocId()
                    )).toList();
            Person personEnt = persons.findById(wpp.getPersonId()).orElse(null);
            Boolean hasPhoto = personEnt != null && personEnt.getPhotoKey() != null;
            AttendanceSession pSession = todaySessionByPerson.get(wpp.getPersonId());
            bestByResource.put(key, new FieldDeploymentBoardItem(
                    wpp.getId(), OwnerType.PERSON, wpp.getPersonId(), label,
                    hasPhoto, wp.getId(),
                    wpp.getSupplierCompanyId(), supName,
                    wp.getSiteId(), site != null ? site.getName() : null,
                    site != null ? site.getLatitude() : null,
                    site != null ? site.getLongitude() : null,
                    wp.getWorkDate(), null,
                    totalDays, totalHours, lastDate, todayAttended,
                    pSession != null ? pSession.getCheckInLat() : null,
                    pSession != null ? pSession.getCheckInLng() : null,
                    pSession != null ? pSession.getCheckOutLat() : null,
                    pSession != null ? pSession.getCheckOutLng() : null,
                    recent
            ));
        }
        return new java.util.ArrayList<>(bestByResource.values());
    }

    // ──────────────────────────────────────────────────────────────────

    private FieldDeploymentRequest getOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() ->
                ApiException.notFound("DEPLOY_NOT_FOUND", "투입 요청 없음"));
    }

    private FieldDeploymentResponse toResponse(FieldDeploymentRequest r) {
        Map<Long, String> cache = new HashMap<>();
        String supName = companyName(r.getSupplierCompanyId(), cache);
        String bpName = companyName(r.getBpCompanyId(), cache);
        String resLabel = resourceLabel(r.getResourceType(), r.getResourceId());
        String siteName = r.getTargetSiteId() != null
                ? sites.findById(r.getTargetSiteId()).map(Site::getName).orElse(null)
                : null;
        // R3: 조합 스냅샷 행이면 목록 묶음 헤더용 장비 라벨 동봉.
        String comboLabel = r.getComboEquipmentId() != null
                ? resourceLabel(OwnerType.EQUIPMENT, r.getComboEquipmentId()) : null;
        return FieldDeploymentResponse.from(r, supName, bpName, resLabel, siteName, comboLabel);
    }

    private String companyName(Long id, Map<Long, String> cache) {
        if (id == null) return null;
        if (cache.containsKey(id)) return cache.get(id);
        String name = companies.findById(id).map(Company::getName).orElse(null);
        cache.put(id, name);
        return name;
    }

    private String resourceLabel(OwnerType type, Long id) {
        if (type == OwnerType.EQUIPMENT) {
            return equipments.findById(id)
                    .map(e -> e.getVehicleNo() != null ? e.getVehicleNo() : e.getModel())
                    .orElse("#" + id);
        }
        if (type == OwnerType.PERSON) {
            return persons.findById(id).map(Person::getName).orElse("#" + id);
        }
        return "#" + id;
    }
}
