package com.skep.company;

import com.skep.company.dto.ChildRollupResponse;
import com.skep.document.DocumentRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.readiness.ResourceReadinessResponse;
import com.skep.readiness.ResourceReadinessService;
import com.skep.security.AuthenticatedUser;
import com.skep.user.CompanyUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * B4: 부모 master 가 직속 자식(협력사) 공급사별 롤업을 조회. 기존 서비스만 재사용하는 읽기전용 집계.
 * 격리: 직속 자식(CompanyService.listChildren)만 순회하고, 가입대기 조회는 CompanyUserService.listChildUsers
 *       (ensureMaster + ensureDirectChild 내장)로 자식 검증을 위임한다.
 */
@Service
@RequiredArgsConstructor
public class ChildRollupService {

    private final CompanyService companyService;
    private final ResourceReadinessService readinessService;
    private final CompanyUserService companyUsers;
    private final DocumentRepository documents;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;

    @Transactional(readOnly = true)
    public List<ChildRollupResponse> rollupForParent(AuthenticatedUser actor) {
        List<Company> children = companyService.listChildren(actor.companyId());
        LocalDate maxDate = LocalDate.now().plusDays(30);
        List<ChildRollupResponse> out = new ArrayList<>();
        for (Company child : children) {
            Long childId = child.getId();

            // readiness — 자식 1건 스코프로 게이트 술어 미러링 재사용.
            List<ResourceReadinessResponse> readiness = readinessService.listForScope(List.of(childId));
            long ready = readiness.stream().filter(ResourceReadinessResponse::ready).count();
            long pending = readiness.size() - ready;

            // 만료 임박 서류 — 자식 소유 장비·인원 owner_id 로 좁혀 카운트(공급사 대시보드와 동일 패턴).
            List<Long> eqIds = equipmentRepo.findBySupplierIdInOrderByIdDesc(List.of(childId)).stream()
                    .map(Equipment::getId).toList();
            List<Long> personIds = personRepo.findBySupplierIdInOrderByIdDesc(List.of(childId)).stream()
                    .map(Person::getId).toList();
            long expiring = (eqIds.isEmpty() ? 0L
                        : documents.countExpiringForOwners(OwnerType.EQUIPMENT, eqIds, maxDate))
                    + (personIds.isEmpty() ? 0L
                        : documents.countExpiringForOwners(OwnerType.PERSON, personIds, maxDate));

            // 가입 대기 유저 — listChildUsers 가 직속 자식 검증(격리) 내장.
            long pendingUsers = companyUsers.listChildUsers(childId, actor).stream()
                    .filter(u -> !u.isEnabled()).count();

            out.add(new ChildRollupResponse(childId, child.getName(), ready, pending, expiring, pendingUsers));
        }
        return out;
    }
}
