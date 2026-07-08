package com.skep.compliance;

import com.skep.common.ApiException;
import com.skep.compliance.dto.ResourceCompliance;
import com.skep.compliance.dto.SiteCompliance;
import com.skep.equipment.EquipmentService;
import com.skep.person.PersonService;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-11: 자원 서류 컴플라이언스 조회 API.
 *  GET /api/sites/{id}/compliance       사이트 통합 (BP + 활성 공급사 자원)
 *  GET /api/companies/{id}/compliance   회사 단독 (BP/공급사)
 *  GET /api/equipment/{id}/compliance   장비 1건
 *  GET /api/persons/{id}/compliance     인원 1건
 *
 * P1 권한 가드: 각 도메인 Service.get(id, actor) 를 먼저 호출하여
 * IDOR 차단. 통과 후에만 compliance 평가.
 */
@RestController
@RequestMapping("/api")
public class ComplianceController {

    private final ComplianceService service;
    private final EquipmentService equipmentService;
    private final PersonService personService;

    public ComplianceController(ComplianceService service,
                                 EquipmentService equipmentService,
                                 PersonService personService) {
        this.service = service;
        this.equipmentService = equipmentService;
        this.personService = personService;
    }

    @GetMapping("/sites/{id}/compliance")
    public SiteCompliance siteCompliance(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.forSite(id, actor);
    }

    @GetMapping("/companies/{id}/compliance")
    public ResourceCompliance companyCompliance(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        // P1: ADMIN 또는 본인 회사만 조회 허용. (다른 회사 서류 노출 차단)
        if (actor.role() != Role.ADMIN
                && (actor.companyId() == null || !actor.companyId().equals(id))) {
            throw ApiException.forbidden("COMPLIANCE_VIEW_DENIED",
                    "본인 회사의 컴플라이언스만 조회할 수 있습니다");
        }
        return service.forCompany(id, actor);
    }

    @GetMapping("/equipment/{id}/compliance")
    public ResourceCompliance equipmentCompliance(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        // P1: EquipmentService.get 가 권한 검증 — 통과 못하면 throw
        equipmentService.get(id, actor);
        return service.forEquipment(id, actor);
    }

    @GetMapping("/persons/{id}/compliance")
    public ResourceCompliance personCompliance(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        personService.get(id, actor);
        return service.forPerson(id, actor);
    }
}
