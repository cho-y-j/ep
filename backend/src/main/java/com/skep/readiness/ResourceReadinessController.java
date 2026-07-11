package com.skep.readiness;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 투입 대기 가시성 — 공급사/협력사 본인+자식 소유 자원의 투입 준비 상태(읽기전용). */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceReadinessController {

    private final ResourceReadinessService service;

    /** 자원별 투입 준비 상태. actor 스코프(본인+직속 자식)로 격리, 그 외 역할은 빈 목록. */
    @GetMapping("/readiness")
    public List<ResourceReadinessResponse> readiness(@CurrentUser AuthenticatedUser actor) {
        return service.listForActor(actor);
    }
}
