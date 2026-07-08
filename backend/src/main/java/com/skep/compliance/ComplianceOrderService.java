package com.skep.compliance;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.compliance.dto.*;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplianceOrderService {

    private final ComplianceOrderRepository orders;
    private final CompanyRepository companies;
    private final EquipmentRepository equipments;
    private final PersonRepository persons;
    private final NotificationService notifications;

    /** BP/ADMIN 이행지시 발행. */
    @Transactional
    public ComplianceOrderResponse issue(CreateComplianceOrderRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ONLY", "BP/ADMIN 만 발행 가능합니다");
        }
        Long bpCompanyId = actor.role() == Role.ADMIN ? null : actor.companyId();
        if (bpCompanyId == null && actor.role() == Role.ADMIN) {
            // ADMIN 대행: supplier 의 거래 BP가 모호하므로 — 일단 ADMIN 본인 회사 또는 null 처리.
            // 단순화: ADMIN은 임의 BP를 지정 못함. ADMIN 대행 발행은 별도 UI 추가 시 처리.
            throw ApiException.badRequest("ADMIN_BP_CONTEXT_REQUIRED", "ADMIN 발행은 별도 흐름 필요");
        }
        // 대상 자원 검증 — 공급사 본인 자원이어야
        verifyTargetOwnership(req.targetType(), req.targetId(), req.supplierCompanyId());

        ComplianceOrder o = ComplianceOrder.builder()
                .bpCompanyId(bpCompanyId)
                .supplierCompanyId(req.supplierCompanyId())
                .targetType(req.targetType())
                .targetId(req.targetId())
                .orderType(req.orderType())
                .orderSubtype(req.orderSubtype())
                .dueDate(req.dueDate())
                .requestNotes(req.requestNotes())
                .createdBy(actor.id())
                .build();
        orders.save(o);

        // 알림 — 공급사에게
        String targetLabel = resolveTargetLabel(req.targetType(), req.targetId());
        String orderTypeLabel = orderTypeLabel(req.orderType());
        notifications.sendToCompany(req.supplierCompanyId(),
                "COMPLIANCE_ORDER",
                "이행지시 도착",
                orderTypeLabel + " — " + targetLabel + " (마감 " + req.dueDate() + ")",
                "COMPLIANCE_ORDER", o.getId(), null);

        return toResponse(o);
    }

    /** 공급사 본인이 자기 회사 지시 list. */
    @Transactional(readOnly = true)
    public List<ComplianceOrderResponse> listForSupplier(AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return orders.findAll().stream().map(this::toResponse).toList();
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 가능");
        }
        return orders.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream().map(this::toResponse).toList();
    }

    /** BP 본인이 발행한 지시 list. */
    @Transactional(readOnly = true)
    public List<ComplianceOrderResponse> listForBp(AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return orders.findAll().stream().map(this::toResponse).toList();
        if (actor.role() != Role.BP) {
            throw ApiException.forbidden("BP_ONLY", "BP만 가능");
        }
        return orders.findByBpCompanyIdOrderByIdDesc(actor.companyId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ComplianceOrderResponse get(Long id, AuthenticatedUser actor) {
        ComplianceOrder o = orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "지시 없음"));
        ensureCanView(o, actor);
        return toResponse(o);
    }

    /** 공급사 — 증빙 메모 + (선택) 파일 업로드는 별도 endpoint 로 처리. status SUBMITTED 전환. */
    @Transactional
    public ComplianceOrderResponse submit(Long id, SubmitComplianceRequest req, AuthenticatedUser actor) {
        ComplianceOrder o = orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "지시 없음"));
        if (!isSameSupplier(o, actor)) {
            throw ApiException.forbidden("NOT_OWNER", "본인 회사에 발행된 지시만 제출 가능");
        }
        if (o.getStatus() == ComplianceOrderStatus.APPROVED) {
            throw ApiException.conflict("ALREADY_APPROVED", "이미 승인된 지시입니다");
        }
        if (o.getProofStorageKey() == null) {
            throw ApiException.badRequest("PROOF_REQUIRED", "증빙 파일을 먼저 업로드하세요");
        }
        o.submit(req.submissionNotes(), null, null, null); // file은 별도 upload 후 메서드에서 갱신됨
        // BP 알림
        notifications.sendToCompany(o.getBpCompanyId(),
                "COMPLIANCE_ORDER_SUBMITTED",
                "이행 완료 보고",
                orderTypeLabel(o.getOrderType()) + " — " + resolveTargetLabel(o.getTargetType(), o.getTargetId()),
                "COMPLIANCE_ORDER", o.getId(), null);
        return toResponse(o);
    }

    /** BP — 승인 또는 반려. */
    @Transactional
    public ComplianceOrderResponse review(Long id, ReviewComplianceRequest req, AuthenticatedUser actor) {
        ComplianceOrder o = orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "지시 없음"));
        if (!isSameBp(o, actor)) {
            throw ApiException.forbidden("NOT_BP", "본인 회사가 발행한 지시만 검토 가능");
        }
        if (o.getStatus() != ComplianceOrderStatus.SUBMITTED) {
            throw ApiException.badRequest("NOT_SUBMITTED", "제출된 지시만 검토 가능");
        }
        if (req.approve()) {
            o.approve(actor.id());
            notifications.sendToCompany(o.getSupplierCompanyId(),
                    "COMPLIANCE_ORDER_APPROVED",
                    "이행 승인됨",
                    orderTypeLabel(o.getOrderType()) + " — " + resolveTargetLabel(o.getTargetType(), o.getTargetId()),
                    "COMPLIANCE_ORDER", o.getId(), null);
        } else {
            if (req.rejectionReason() == null || req.rejectionReason().isBlank()) {
                throw ApiException.badRequest("REASON_REQUIRED", "반려 사유를 입력하세요");
            }
            o.reject(actor.id(), req.rejectionReason());
            notifications.sendToCompany(o.getSupplierCompanyId(),
                    "COMPLIANCE_ORDER_REJECTED",
                    "이행 반려됨",
                    orderTypeLabel(o.getOrderType()) + " — " + req.rejectionReason(),
                    "COMPLIANCE_ORDER", o.getId(), null);
        }
        return toResponse(o);
    }

    /** 증빙 파일 storage key를 직접 갱신 — Controller 의 file upload 메서드가 호출. */
    @Transactional
    public void attachProof(Long id, String storageKey, String filename, String contentType, AuthenticatedUser actor) {
        ComplianceOrder o = orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "지시 없음"));
        if (!isSameSupplier(o, actor)) {
            throw ApiException.forbidden("NOT_OWNER", "본인 회사 지시만 가능");
        }
        if (o.getStatus() == ComplianceOrderStatus.APPROVED) {
            throw ApiException.conflict("ALREADY_APPROVED", "이미 승인된 지시는 증빙 변경 불가");
        }
        // 그냥 추가 — submit() 의 storageKey 인자가 null 인 경우와 별도로 분리.
        // 가장 단순한 방법: native update via repo or refresh entity. 여기는 entity 직접 노출하지 않고 submit() helper 재사용:
        o.submit(o.getSubmissionNotes(), storageKey, filename, contentType);
        // 단, status 가 REQUESTED 였다면 submit() 이 SUBMITTED 로 바꿈 — 의도 아닐 수도 있으나 증빙 = 제출로 간주. UX 도 그러함.
    }

    /** 증빙 파일 다운로드 용. 권한 확인 후 (storageKey, filename, contentType) 반환. */
    @Transactional(readOnly = true)
    public ProofInfo getProofInfo(Long id, AuthenticatedUser actor) {
        ComplianceOrder o = orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "지시 없음"));
        ensureCanView(o, actor);
        if (o.getProofStorageKey() == null) {
            throw ApiException.notFound("NO_PROOF", "증빙이 없습니다");
        }
        return new ProofInfo(o.getProofStorageKey(), o.getProofFilename(), o.getProofContentType());
    }

    public record ProofInfo(String storageKey, String filename, String contentType) {}

    /** WorkPlanService.start 게이트용 — 자원들에 대해 차단할 미이행 order 가 있는지. */
    @Transactional(readOnly = true)
    public List<ComplianceOrder> findBlockingFor(ComplianceTargetType targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) return List.of();
        return orders.findBlockingForTargets(targetType, targetIds, java.time.LocalDate.now());
    }

    private void verifyTargetOwnership(ComplianceTargetType type, Long id, Long supplierId) {
        if (type == ComplianceTargetType.VEHICLE) {
            Equipment e = equipments.findById(id)
                    .orElseThrow(() -> ApiException.badRequest("TARGET_NOT_FOUND", "차량 없음"));
            if (!supplierId.equals(e.getSupplierId())) {
                throw ApiException.badRequest("TARGET_NOT_OWNED", "해당 공급사 차량 아님");
            }
        } else {
            Person p = persons.findById(id)
                    .orElseThrow(() -> ApiException.badRequest("TARGET_NOT_FOUND", "인원 없음"));
            if (!supplierId.equals(p.getSupplierId())) {
                throw ApiException.badRequest("TARGET_NOT_OWNED", "해당 공급사 인원 아님");
            }
        }
    }

    private String resolveTargetLabel(ComplianceTargetType type, Long id) {
        if (type == ComplianceTargetType.VEHICLE) {
            return equipments.findById(id).map(e -> e.getVehicleNo() != null ? e.getVehicleNo()
                    : (e.getModel() != null ? e.getModel() : "차량#" + id))
                    .orElse("차량#" + id);
        }
        return persons.findById(id).map(Person::getName).orElse("인원#" + id);
    }

    private boolean isSameSupplier(ComplianceOrder o, AuthenticatedUser actor) {
        return actor.role() == Role.ADMIN
                || (actor.companyId() != null && actor.companyId().equals(o.getSupplierCompanyId()));
    }
    private boolean isSameBp(ComplianceOrder o, AuthenticatedUser actor) {
        return actor.role() == Role.ADMIN
                || (actor.companyId() != null && actor.companyId().equals(o.getBpCompanyId()));
    }
    private void ensureCanView(ComplianceOrder o, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        Long cid = actor.companyId();
        if (cid == null || (!cid.equals(o.getBpCompanyId()) && !cid.equals(o.getSupplierCompanyId()))) {
            throw ApiException.forbidden("ORDER_VIEW_DENIED", "권한 없음");
        }
    }

    private static String orderTypeLabel(ComplianceOrderType t) {
        return switch (t) {
            case SAFETY_INSPECTION -> "안전점검";
            case HEALTH_CHECK -> "건강검진";
            case OTHER -> "기타 이행지시";
        };
    }

    private ComplianceOrderResponse toResponse(ComplianceOrder o) {
        Map<Long, String> names = companies.findAllById(List.of(o.getBpCompanyId(), o.getSupplierCompanyId())).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));
        return ComplianceOrderResponse.from(o,
                names.get(o.getBpCompanyId()),
                names.get(o.getSupplierCompanyId()),
                resolveTargetLabel(o.getTargetType(), o.getTargetId()));
    }
}
