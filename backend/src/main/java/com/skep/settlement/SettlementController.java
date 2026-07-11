package com.skep.settlement;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.settlement.dto.SettlementDtos.SettlementSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 소유자별 투입 정산 요약. 공급사=본인+협력사, ADMIN=companyId 지정, BP=403. */
@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService service;

    @GetMapping("/api/settlements/summary")
    public SettlementSummaryResponse summary(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.summary(actor, companyId, from, to);
    }

    /** 투입 관리 — 배차행 정산용 근무일수·OT일수 입력(수정). type=EQUIPMENT|PERSON. */
    @PatchMapping("/api/settlements/dispatch/{type}/{id}/quantity")
    public void setQuantity(
            @PathVariable String type, @PathVariable Long id,
            @RequestBody QuantityRequest req, @CurrentUser AuthenticatedUser actor
    ) {
        service.setQuantity(actor, type, id, req.workDays(), req.otDays());
    }

    public record QuantityRequest(Integer workDays, Integer otDays) {}
}
