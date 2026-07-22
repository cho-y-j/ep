package com.skep.company;

import com.skep.common.ApiException;
import com.skep.equipment.EquipmentTypeRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회사가 취급(선택)한 장비종류 설정. 공급사(장비업체)가 자주 쓰는 종류만 지정해 두면
 * 장비 등록·서류 수집요청의 종류 선택이 그 종류만 기본 표시된다. 빈 집합 = 전체 표시(기존 동작).
 * 조회는 본인 회사 스코프, 저장은 장비공급사 master 전용.
 */
@Service
@Transactional
public class CompanyEquipmentTypeService {

    private final CompanyEquipmentTypeRepository repo;
    private final EquipmentTypeRepository typeRepo;

    public CompanyEquipmentTypeService(CompanyEquipmentTypeRepository repo, EquipmentTypeRepository typeRepo) {
        this.repo = repo;
        this.typeRepo = typeRepo;
    }

    /** 내 회사 취급 장비종류 코드 목록. 회사 없음/미설정이면 빈 목록(= 전체 표시). */
    @Transactional(readOnly = true)
    public List<String> list(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return repo.findByCompanyId(actor.companyId()).stream()
                .map(CompanyEquipmentType::getEquipmentTypeCode)
                .toList();
    }

    /** 전체 교체(replace-all). 존재하는 종류 코드만 반영. 장비공급사 master 전용. */
    public List<String> replace(List<String> codes, AuthenticatedUser actor) {
        ensureEquipmentMaster(actor);
        Long companyId = actor.companyId();
        Set<String> desired = codes == null ? Set.of()
                : codes.stream().filter(typeRepo::existsById)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        List<CompanyEquipmentType> existing = repo.findByCompanyId(companyId);
        Set<String> existingCodes = existing.stream()
                .map(CompanyEquipmentType::getEquipmentTypeCode)
                .collect(Collectors.toSet());
        repo.deleteAll(existing.stream()
                .filter(e -> !desired.contains(e.getEquipmentTypeCode()))
                .toList());
        repo.saveAll(desired.stream()
                .filter(c -> !existingCodes.contains(c))
                .map(c -> new CompanyEquipmentType(companyId, c))
                .toList());
        return List.copyOf(desired);
    }

    private void ensureEquipmentMaster(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        }
        if (actor.role() != Role.EQUIPMENT_SUPPLIER || !actor.isCompanyAdmin()) {
            throw ApiException.forbidden("NOT_EQUIPMENT_MASTER", "장비공급사 관리자만 설정할 수 있습니다");
        }
    }
}
