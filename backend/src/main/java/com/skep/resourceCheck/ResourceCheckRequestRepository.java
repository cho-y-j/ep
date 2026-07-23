package com.skep.resourceCheck;

import com.skep.document.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResourceCheckRequestRepository extends JpaRepository<ResourceCheckRequest, Long> {

    /** 발행 목록 (자기 회사 발행분 — bp_company_id = 발행사, BP/공급사 공통). */
    List<ResourceCheckRequest> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    /** 공급사 수신함 (본인 + 직속 자식 협력사가 받은 것 — V77 self+children). */
    List<ResourceCheckRequest> findBySupplierCompanyIdInOrderByIdDesc(Collection<Long> supplierCompanyIds);

    /** 작업계획서별 점검 요청 (BP/공급사 공통 조회용). */
    List<ResourceCheckRequest> findByWorkPlanIdOrderByIdDesc(Long workPlanId);

    /** 특정 자원에 발행된 요청 — 자원 상태 매핑용. */
    List<ResourceCheckRequest> findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType ownerType, Long ownerId);

    /** 투입 준비 가시성 — 여러 자원의 점검 요청 일괄 조회(N+1 회피). */
    List<ResourceCheckRequest> findByOwnerTypeAndOwnerIdIn(OwnerType ownerType, Collection<Long> ownerIds);
}
