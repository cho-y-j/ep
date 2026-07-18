package com.skep.onboarding;

import com.skep.document.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SiteResourceOnboardingRepository extends JpaRepository<SiteResourceOnboarding, Long> {

    /** 공급사 자기 회사 신고 이력. */
    List<SiteResourceOnboarding> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    /** BP 자기 앞 소급 건(대기 + 처리 완료). */
    List<SiteResourceOnboarding> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    /** 자원 1건의 특정 상태(확정 배지용) 온보딩. */
    List<SiteResourceOnboarding> findByOwnerTypeAndOwnerIdAndModeInOrderByIdDesc(
            OwnerType ownerType, Long ownerId, Collection<OnboardingMode> modes);
}
