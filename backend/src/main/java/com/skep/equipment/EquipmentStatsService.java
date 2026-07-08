package com.skep.equipment;

import com.skep.common.ApiException;
import com.skep.equipment.dto.EquipmentDeploymentRow;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.workplan.WorkPlanEquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Phase3 장비 투입 통계 — 공급사 장비가 어느 BP 회사에서 몇 번 일했나. */
@Service
public class EquipmentStatsService {

    private final WorkPlanEquipmentRepository wpeRepo;

    public EquipmentStatsService(WorkPlanEquipmentRepository wpeRepo) {
        this.wpeRepo = wpeRepo;
    }

    /** EQUIPMENT_SUPPLIER = 본인 회사 장비 / ADMIN = supplierId 지정 필수. */
    @Transactional(readOnly = true)
    public List<EquipmentDeploymentRow> deployments(AuthenticatedUser actor, Long supplierIdParam) {
        Long supplierId;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            supplierId = actor.companyId();
        } else if (actor.role() == Role.ADMIN) {
            if (supplierIdParam == null) {
                throw ApiException.badRequest("SUPPLIER_REQUIRED", "supplierId 가 필요합니다");
            }
            supplierId = supplierIdParam;
        } else {
            throw ApiException.forbidden("FORBIDDEN", "조회 권한이 없습니다");
        }
        if (supplierId == null) return List.of();

        return wpeRepo.findDeploymentStats(supplierId).stream()
                .map(r -> new EquipmentDeploymentRow(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        (String) r[2],
                        (String) r[3],
                        r[4] != null && (Boolean) r[4],
                        (String) r[5],
                        r[6] != null ? ((Number) r[6]).longValue() : null,
                        (String) r[7],
                        ((Number) r[8]).longValue()
                ))
                .toList();
    }
}
