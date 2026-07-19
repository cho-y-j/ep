package com.skep.safety;

import com.skep.safety.EmergencyResponseService.PeerCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5-W2 근접 동료 선정 순수 로직 — ①같은 작업조 우선 ②거리 오름차순 ③personId(결정론).
 * 좌표 결측 시 거리=최대(맨 뒤), (a) 없이 (b)+id 로 정렬.
 */
class EmergencyResponseServiceTest {

    // 서울 시청 근방 — 위경도 소차 = 근거리.
    private static final double LAT = 37.5665;
    private static final double LNG = 126.9780;

    @Test
    void sameGroup_matchesOnlyWhenBothPresentAndEqual() {
        assertTrue(EmergencyResponseService.sameGroup(10L, 10L));
        assertFalse(EmergencyResponseService.sameGroup(10L, 20L));
        assertFalse(EmergencyResponseService.sameGroup(null, 10L));   // 피재자 계획서 미상.
        assertFalse(EmergencyResponseService.sameGroup(10L, null));
    }

    @Test
    void distance_zeroForSamePoint_maxForMissing() {
        assertEquals(0.0, EmergencyResponseService.distanceOrMax(LAT, LNG, LAT, LNG), 0.001);
        assertEquals(Double.MAX_VALUE, EmergencyResponseService.distanceOrMax(LAT, LNG, null, LNG));
        assertEquals(Double.MAX_VALUE, EmergencyResponseService.distanceOrMax(null, null, LAT, LNG));
        // 근거리 두 점은 양수·유한.
        double d = EmergencyResponseService.distanceOrMax(LAT, LNG, LAT + 0.001, LNG);
        assertTrue(d > 0 && d < 1000);
    }

    @Test
    void rankPeers_sameGroupOutranksCloserOtherGroup() {
        // 피재자: 계획서 10, 위 좌표. p1 = 다른조지만 바로 옆(가까움). p2 = 같은조지만 100m 밖.
        PeerCandidate p1 = new PeerCandidate(1L, 20L, LAT, LNG);                 // 다른조·최근접.
        PeerCandidate p2 = new PeerCandidate(2L, 10L, LAT + 0.001, LNG + 0.001); // 같은조·약간 멀리.
        List<Long> ranked = EmergencyResponseService.rankPeers(10L, LAT, LNG, List.of(p1, p2), 3);
        assertEquals(List.of(2L, 1L), ranked);   // 같은조(p2) 우선.
    }

    @Test
    void rankPeers_withinSameGroupClosestFirst() {
        PeerCandidate near = new PeerCandidate(1L, 10L, LAT + 0.0005, LNG);   // 가까움.
        PeerCandidate far = new PeerCandidate(2L, 10L, LAT + 0.01, LNG);      // 멈.
        List<Long> ranked = EmergencyResponseService.rankPeers(10L, LAT, LNG, List.of(far, near), 3);
        assertEquals(List.of(1L, 2L), ranked);   // 가까운(near) 먼저.
    }

    @Test
    void rankPeers_missingCoordsGoLast_limitRespected() {
        PeerCandidate withCoord = new PeerCandidate(1L, 20L, LAT, LNG);
        PeerCandidate noCoord = new PeerCandidate(2L, 20L, null, null);
        List<Long> ranked = EmergencyResponseService.rankPeers(10L, LAT, LNG, List.of(noCoord, withCoord), 3);
        assertEquals(List.of(1L, 2L), ranked);   // 좌표 있는 쪽 먼저.
    }

    @Test
    void rankPeers_limitCapsResult() {
        List<PeerCandidate> five = List.of(
                new PeerCandidate(1L, 10L, LAT, LNG),
                new PeerCandidate(2L, 10L, LAT + 0.001, LNG),
                new PeerCandidate(3L, 10L, LAT + 0.002, LNG),
                new PeerCandidate(4L, 10L, LAT + 0.003, LNG),
                new PeerCandidate(5L, 10L, LAT + 0.004, LNG));
        List<Long> ranked = EmergencyResponseService.rankPeers(10L, LAT, LNG, five, 3);
        assertEquals(List.of(1L, 2L, 3L), ranked);   // 상위 3인만.
    }

    @Test
    void rankPeers_noLocationAnywhere_deterministicByGroupThenId() {
        // 피재자 GPS 없음 + 동료 좌표 없음 → (a) 없음. 같은조 우선, 그다음 personId.
        PeerCandidate a = new PeerCandidate(3L, 10L, null, null);   // 같은조.
        PeerCandidate b = new PeerCandidate(1L, 20L, null, null);   // 다른조·낮은 id.
        PeerCandidate c = new PeerCandidate(2L, 10L, null, null);   // 같은조·낮은 id.
        List<Long> ranked = EmergencyResponseService.rankPeers(10L, null, null, List.of(a, b, c), 3);
        assertEquals(List.of(2L, 3L, 1L), ranked);   // 같은조(2,3 by id) → 다른조(1).
    }
}
