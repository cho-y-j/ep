package com.skep.field;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * BP·공급사·ADMIN 공용 "현장 상황" 통합 조회. 현장 하나의 6요소를 한 번에 반환.
 * JWT Bearer 인증 (SecurityConfig anyRequest().authenticated()) + @PreAuthorize 역할 제한.
 */
@RestController
@RequestMapping("/api/field")
@RequiredArgsConstructor
public class SiteSituationController {

    private final SiteSituationService service;

    @GetMapping("/site-situation/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public Map<String, Object> situation(@PathVariable Long siteId,
                                         @CurrentUser AuthenticatedUser actor) {
        return service.situation(siteId, actor);
    }
}
