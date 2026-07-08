package com.skep.resourceCheck;

import com.skep.document.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceCheckRequestRepository extends JpaRepository<ResourceCheckRequest, Long> {

    /** BP 발송 목록 (자기 회사 발송분). */
    List<ResourceCheckRequest> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    /** 공급사 수신함 (자기 회사가 받은 것). */
    List<ResourceCheckRequest> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    /** 작업계획서별 점검 요청 (BP/공급사 공통 조회용). */
    List<ResourceCheckRequest> findByWorkPlanIdOrderByIdDesc(Long workPlanId);

    /** 특정 자원에 발행된 요청 — 자원 상태 매핑용. */
    List<ResourceCheckRequest> findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType ownerType, Long ownerId);
}
