package com.skep.legalinspection;

import com.skep.common.ApiException;
import com.skep.field.FieldTokenAuth;
import com.skep.field.FieldTokenRateLimiter;
import com.skep.legalinspection.dto.InspectorDtos.*;
import com.skep.legalinspection.dto.SafetyCheckTemplateDtos;
import com.skep.person.Person;
import com.skep.person.PersonRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 점검원(안전점검회사 소속) 모바일웹 흐름 — 자가로그인(X-Field-Token) 재사용.
 * 점검원 = INSPECTOR 역할 Person. NFC 강제: 태그 검증 성공(open)해야 체크리스트 제출(submit) 가능.
 */
@RestController
@RequestMapping("/api/field-auth/inspector")
@RequiredArgsConstructor
public class InspectorController {

    private final FieldTokenAuth fieldAuth;
    private final FieldTokenRateLimiter rateLimiter;
    private final LegalInspectionService service;

    /** 활성 점검 템플릿(체크리스트 렌더용). */
    @GetMapping("/template")
    public SafetyCheckTemplateDtos.Response template(@RequestHeader("X-Field-Token") String token,
                                                     HttpServletRequest request) {
        rateLimiter.check(request);
        requireInspector(token);
        return service.activeTemplate();
    }

    /** 오늘 점검 대상 — 배치 장비 목록(현장 필터·완료 배지). */
    @GetMapping("/targets")
    public List<TargetItem> targets(@RequestHeader("X-Field-Token") String token,
                                    @RequestParam(required = false) Long siteId,
                                    HttpServletRequest request) {
        rateLimiter.check(request);
        requireInspector(token);
        return service.targets(siteId);
    }

    /** NFC 태그하여 점검 시작 — 태그 검증 후 오픈 토큰 발급. */
    @PostMapping("/open")
    @Transactional
    public OpenResponse open(@RequestHeader("X-Field-Token") String token,
                             @RequestBody OpenRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person inspector = requireInspector(token);
        return service.open(inspector, req);
    }

    /** 체크리스트+서명 제출 — 오픈 토큰 재검증 + 필수항목 가드. */
    @PostMapping("/submit")
    @Transactional
    public EvidenceResponse submit(@RequestHeader("X-Field-Token") String token,
                                   @RequestBody SubmitRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person inspector = requireInspector(token);
        return service.submit(inspector, req);
    }

    private Person requireInspector(String token) {
        Person p = fieldAuth.authenticate(token);
        if (!p.getRoles().contains(PersonRole.INSPECTOR)) {
            throw ApiException.forbidden("NOT_INSPECTOR", "점검원 권한이 없습니다");
        }
        return p;
    }
}
