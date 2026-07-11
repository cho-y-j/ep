package com.skep.pipeline;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 자원 파이프라인 — 공급사/협력사 본인+자식 소유 자원의 6단계 상태(읽기전용 집계). */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourcePipelineController {

    private final ResourcePipelineService service;

    /** 자원별 파이프라인 상태. actor 스코프(본인+직속 자식)로 격리, 그 외 역할은 빈 목록. */
    @GetMapping("/pipeline")
    public List<ResourcePipelineResponse> pipeline(@CurrentUser AuthenticatedUser actor) {
        return service.listForActor(actor);
    }
}
