package com.skep.dashboard;

import com.skep.dashboard.dto.DashboardSummary;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(@CurrentUser AuthenticatedUser actor) {
        return service.summary(actor);
    }
}
