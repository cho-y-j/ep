package com.skep.workplan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3a S3 — 당일 조종원 일일점검 게이트의 순수 판정(미점검 장비 목록).
 */
class WorkPlanInspectionGateTest {

    @Test
    void returnsOnlyUninspected() {
        assertEquals(List.of(2L),
                WorkPlanService.equipmentMissingDailyInspection(List.of(1L, 2L, 3L), Set.of(1L, 3L)));
    }

    @Test
    void allInspectedIsEmpty() {
        assertTrue(WorkPlanService.equipmentMissingDailyInspection(List.of(1L, 2L), Set.of(1L, 2L)).isEmpty());
    }

    @Test
    void noneInspectedReturnsAll() {
        assertEquals(List.of(1L, 2L),
                WorkPlanService.equipmentMissingDailyInspection(List.of(1L, 2L), Set.of()));
    }

    @Test
    void emptyPlanIsEmpty() {
        assertTrue(WorkPlanService.equipmentMissingDailyInspection(List.of(), Set.of(1L)).isEmpty());
    }
}
