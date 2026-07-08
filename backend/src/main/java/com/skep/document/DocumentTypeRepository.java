package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    List<DocumentType> findByAppliesToAndActiveOrderBySortOrderAscIdAsc(OwnerType appliesTo, boolean active);

    /** 자원 점검 회신용 — 이름 + applies_to 로 정확 lookup. */
    Optional<DocumentType> findByNameAndAppliesTo(String name, OwnerType appliesTo);
    List<DocumentType> findAllByOrderByAppliesToAscSortOrderAscIdAsc();

    /** 자원 후보 미배차 판단용: 해당 owner_type 의 active+required 인 서류 타입. (표시용 카운트) */
    List<DocumentType> findByAppliesToAndRequiredTrueAndActiveTrueOrderByIdAsc(OwnerType appliesTo);

    /** 배차 차단 검사용: 해당 owner_type 의 active+blocks_assignment 인 서류 타입. (실제 차단 정책) */
    List<DocumentType> findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc(OwnerType appliesTo);

    /** S-11: 컴플라이언스 평가용. */
    List<DocumentType> findByAppliesToAndActiveOrderBySortOrderAsc(OwnerType appliesTo, boolean active);
}
