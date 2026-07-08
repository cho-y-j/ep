package com.skep.equipment;

import com.skep.equipment.dto.EquipmentDeploymentRow;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phase3 장비 투입 통계 API. */
@RestController
@RequestMapping("/api/equipment-stats")
public class EquipmentStatsController {

    private final EquipmentStatsService service;

    public EquipmentStatsController(EquipmentStatsService service) {
        this.service = service;
    }

    /** 공급사 장비별·BP회사별 투입 건수. ADMIN 은 ?supplierId 필수. */
    @GetMapping("/deployments")
    public List<EquipmentDeploymentRow> deployments(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(required = false) Long supplierId
    ) {
        return service.deployments(actor, supplierId);
    }
}
