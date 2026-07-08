package com.skep.supplement;

import com.skep.document.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentSupplementRequestRepository extends JpaRepository<DocumentSupplementRequest, Long> {
    List<DocumentSupplementRequest> findByTargetSupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
    List<DocumentSupplementRequest> findByRequesterUserIdOrderByIdDesc(Long requesterUserId);
    List<DocumentSupplementRequest> findByContextSiteIdOrderByIdDesc(Long siteId);

    /** BP 회사 단위 조회 — 같은 회사 직원이 만든 모든 요청. */
    @Query("""
            SELECT r FROM DocumentSupplementRequest r
            WHERE r.requesterUserId IN (
                SELECT u.id FROM com.skep.user.User u WHERE u.companyId = :companyId
            )
            ORDER BY r.id DESC
            """)
    List<DocumentSupplementRequest> findByRequesterCompanyIdOrderByIdDesc(@Param("companyId") Long companyId);

    /** 자동 resolve 매칭용 — OPEN 상태 + 자원/문서타입 일치. */
    List<DocumentSupplementRequest> findByTargetOwnerTypeAndTargetOwnerIdAndDocumentTypeIdAndStatus(
            OwnerType ownerType, Long ownerId, Long documentTypeId, DocumentSupplementStatus status);

    /** 컴플라이언스 평가용 — 자원 단위 상태별 전체 (문서타입 무관). */
    List<DocumentSupplementRequest> findByTargetOwnerTypeAndTargetOwnerIdAndStatus(
            OwnerType ownerType, Long ownerId, DocumentSupplementStatus status);

    long countByTargetSupplierCompanyIdAndStatus(Long supplierCompanyId, DocumentSupplementStatus status);
}
