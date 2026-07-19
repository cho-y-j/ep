package com.skep.legalinspection;

import com.skep.common.ApiException;
import com.skep.legalinspection.InspectionOpenToken.Payload;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S2′ 법정점검 순수 판정 — 오픈 토큰(발급·재검증·위조·만료) + 필수항목 가드.
 * 태그 검증(올바른 tagId→open, 틀린 tagId→403)은 repo 의존이라 API-레벨 E2E 로 검증.
 */
class LegalInspectionLogicTest {

    private final InspectionOpenToken token = new InspectionOpenToken();

    // ── 오픈 토큰 ──────────────────────────────────────────────

    @Test
    void issueThenVerifyRoundtrips() {
        LocalDateTime readAt = LocalDateTime.now();
        String t = token.issue(10L, 20L, 30L, true, "TAG-ABC", readAt);
        Payload p = token.verify(t);
        assertEquals(10L, p.equipmentId());
        assertEquals(20L, p.inspectorPersonId());
        assertEquals(30L, p.templateId());
        assertTrue(p.verified());                 // NFC 실태그 증명 보존
        assertEquals("TAG-ABC", p.tagId());
    }

    @Test
    void manualFallbackCarriesUnverified() {
        String t = token.issue(1L, 2L, 3L, false, "TAG-X", LocalDateTime.now());
        assertFalse(token.verify(t).verified());  // 수동 폴백 = tag_verified false
    }

    @Test
    void tamperedSignatureRejected() {
        String t = token.issue(1L, 2L, 3L, true, "TAG", LocalDateTime.now());
        String tampered = t.substring(0, t.lastIndexOf('.') + 1) + "AAAAAAAAAAAAAAAAAAAAAAAAAAA";
        ApiException ex = assertThrows(ApiException.class, () -> token.verify(tampered));
        assertEquals("OPEN_TOKEN_INVALID", ex.getCode());
    }

    @Test
    void expiredTokenRejected() {
        // 태그 시각이 TTL(15분) 이전이면 만료.
        String t = token.issue(1L, 2L, 3L, true, "TAG", LocalDateTime.now().minusMinutes(20));
        ApiException ex = assertThrows(ApiException.class, () -> token.verify(t));
        assertEquals("OPEN_TOKEN_EXPIRED", ex.getCode());
    }

    @Test
    void malformedTokenRejected() {
        assertThrows(ApiException.class, () -> token.verify("garbage-no-dot"));
    }

    // ── 필수항목 가드 ─────────────────────────────────────────

    private static Map<String, Object> item(int no, boolean required) {
        return Map.of("no", no, "text", "q" + no, "required", required);
    }

    @Test
    void allRequiredCheckedPasses() {
        List<Map<String, Object>> items = List.of(item(1, true), item(2, true));
        List<Map<String, Object>> result = List.of(
                Map.of("no", 1, "checked", true), Map.of("no", 2, "checked", true));
        assertDoesNotThrow(() -> LegalInspectionService.validateRequiredItems(items, result));
    }

    @Test
    void naCountsAsDone() {
        List<Map<String, Object>> items = List.of(item(1, true));
        List<Map<String, Object>> result = List.of(Map.of("no", 1, "checked", false, "na", true));
        assertDoesNotThrow(() -> LegalInspectionService.validateRequiredItems(items, result));
    }

    @Test
    void requiredUncheckedFails() {
        List<Map<String, Object>> items = List.of(item(1, true), item(2, true));
        List<Map<String, Object>> result = List.of(Map.of("no", 1, "checked", true));  // 2번 누락
        ApiException ex = assertThrows(ApiException.class,
                () -> LegalInspectionService.validateRequiredItems(items, result));
        assertEquals("REQUIRED_ITEM_INCOMPLETE", ex.getCode());
    }

    @Test
    void optionalMissingPasses() {
        List<Map<String, Object>> items = List.of(item(1, true), item(2, false));
        List<Map<String, Object>> result = List.of(Map.of("no", 1, "checked", true));  // 2번(선택) 누락 OK
        assertDoesNotThrow(() -> LegalInspectionService.validateRequiredItems(items, result));
    }
}
