package com.skep.onboarding;

import com.skep.document.OwnerType;
import com.skep.onboarding.dto.CreateOnboardingRequest;
import com.skep.onboarding.dto.OnboardingResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resource-onboardings")
@RequiredArgsConstructor
public class SiteResourceOnboardingController {

    private final SiteResourceOnboardingService service;

    /** 공급사 기투입 등록(REQUESTED 또는 VERBAL). */
    @PostMapping
    public OnboardingResponse create(@Valid @RequestBody CreateOnboardingRequest req,
                                     @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    /** 공급사 자기 신고 이력. */
    @GetMapping("/supplier")
    public List<OnboardingResponse> listSupplier(@CurrentUser AuthenticatedUser actor) {
        return service.listForSupplier(actor);
    }

    /** BP 자기 앞 소급 건(대기 + 처리 완료). */
    @GetMapping("/bp")
    public List<OnboardingResponse> listBp(@CurrentUser AuthenticatedUser actor) {
        return service.listForBp(actor);
    }

    /** 자원 상세 배지용 — 이 자원(장비/인원)의 확정 온보딩(소급 승인/구두승인). */
    @GetMapping("/for-resource")
    public List<OnboardingResponse> listForResource(@RequestParam OwnerType ownerType,
                                                    @RequestParam Long ownerId,
                                                    @CurrentUser AuthenticatedUser actor) {
        return service.listConfirmedForResource(ownerType, ownerId, actor);
    }

    /** BP 소급 승인(단건 — 프론트가 선택분을 반복 호출해 일괄 승인). */
    @PostMapping("/{id}/approve")
    public OnboardingResponse approve(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.approve(id, actor);
    }
}
