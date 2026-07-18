package com.skep.clientcontrol;

import com.skep.clientcontrol.dto.ClientControlDtos.ClientSiteOverview;
import com.skep.clientcontrol.dto.ClientControlDtos.ClientSiteSummary;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 원청(client_org) 통합 관제 허브 — CLIENT 전용, 읽기전용(§1.1). SecurityConfig 에서도 /api/client/** = CLIENT 로 제한. */
@RestController
@RequestMapping("/api/client")
@PreAuthorize("hasRole('CLIENT')")
public class ClientControlController {

    private final ClientControlService service;

    public ClientControlController(ClientControlService service) {
        this.service = service;
    }

    /** 내 원청에 연결된 현장 목록 + 요약. */
    @GetMapping("/sites")
    public List<ClientSiteSummary> sites(@CurrentUser AuthenticatedUser actor) {
        return service.listSites(actor);
    }

    /** 현장 통합 관제 상세 — 타 원청 현장은 403. */
    @GetMapping("/sites/{id}/overview")
    public ClientSiteOverview overview(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.overview(id, actor);
    }
}
