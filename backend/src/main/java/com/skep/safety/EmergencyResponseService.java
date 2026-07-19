package com.skep.safety;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.common.ApiException;
import com.skep.field.FieldFcmService;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P5-W2/W3 서버 대응체인 — 근접 동료 선정·통보(§5.5) + 파인드미 발동/해제(§5.6) + BLE 대리중계 수신(§5.7).
 * 트리거: 개인 응급(FieldSafetyController /emergency)·BLE 릴레이 신규 생성 시. 강풍·폭염(현장 단위)·데드맨
 * (CAUTION·배터리 오경보 위험)은 대상 아님 — 개인 붕괴(SOS/낙상/릴레이)만 발동한다.
 * BLE RSSI 근접 게이지는 현장 도착 후 폰 몫(서버 관여 없음). 서버는 골든타임 시각만 기록.
 */
@Service
@RequiredArgsConstructor
public class EmergencyResponseService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyResponseService.class);

    /** 최초 통보 대상 근접 동료 수. */
    public static final int PEER_LIMIT = 3;
    /** BLE 릴레이 중복 처리 방지 창(분) — victim당 5분 내 재수신은 위치 보강만/스킵. */
    public static final int RELAY_DEDUPE_MIN = 5;

    private final FieldSafetyAlertRepository alertRepo;
    private final PersonRepository persons;
    private final AttendanceSessionRepository attendance;
    private final WorkPlanRepository workPlans;
    private final FieldFcmService fcm;
    private final NotificationService notifications;
    private final SafetyAlertBroadcaster broadcaster;

    /** 근접 동료 선정 입력 1건(순수 로직용) — 같은 현장 열린 세션 인원의 위치/작업조. */
    public record PeerCandidate(Long personId, Long workPlanId, Double lat, Double lng) {}

    // ───────── 순수 로직(단위 테스트 대상) ─────────

    /**
     * 근접 동료 우선순위 선정 — ① 같은 작업계획서(조) 우선 ② 피재자와의 거리 오름차순
     * ③ personId(결정론). 좌표 결측(피재자 GPS 없음/동료 체크인 좌표 없음)이면 거리=최대 → (b) 없이 (a)+id로.
     */
    public static List<Long> rankPeers(Long victimWorkPlanId, Double victimLat, Double victimLng,
                                       List<PeerCandidate> candidates, int limit) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingInt((PeerCandidate c) -> sameGroup(victimWorkPlanId, c.workPlanId()) ? 0 : 1)
                        .thenComparing(c -> distanceOrMax(victimLat, victimLng, c.lat(), c.lng()))
                        .thenComparing(PeerCandidate::personId))
                .limit(Math.max(0, limit))
                .map(PeerCandidate::personId)
                .toList();
    }

    static boolean sameGroup(Long victimWorkPlanId, Long candidateWorkPlanId) {
        return victimWorkPlanId != null && victimWorkPlanId.equals(candidateWorkPlanId);
    }

    /** 좌표 하나라도 결측이면 Double.MAX_VALUE(맨 뒤). 그 외 하버사인 거리(m). */
    static double distanceOrMax(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return Double.MAX_VALUE;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ───────── 대응체인 발동 ─────────

    /**
     * 개인 응급 확정 → 대응체인 발동: ① 피재자 폰 파인드미(find_me) ② 근접 동료 3인 peer_emergency
     * ③ peer_notified_at(t1) 기록. 경보는 호출부가 이미 저장한 관리 엔티티(같은 트랜잭션).
     */
    @Transactional
    public void onEmergencyAlert(FieldSafetyAlert alert, Person victim) {
        sendFindMe(victim, alert.getId());
        List<Long> peerIds = rankPeers(alert.getWorkPlanId(), alert.getLat(), alert.getLng(),
                siteCandidates(alert.getSiteId(), victim.getId()), PEER_LIMIT);
        int notified = notifyPeers(peerIds, alert, victim);
        alert.setPeerNotifiedAt(LocalDateTime.now());
        alertRepo.save(alert);
        log.info("EmergencyResponse chain fire alert={} victim={} peers={}", alert.getId(), victim.getId(), notified);
    }

    /**
     * 60초 무응답 → 현장 전체 확대 + BP·공급사 관리자 통보 + peer_escalated_at(1회 마커).
     * 확대는 원 3인 포함 현장 전체 동료에게 peer_emergency 재발송(보강).
     */
    @Transactional
    public void expandPeers(FieldSafetyAlert alert, Person victim) {
        List<Long> allIds = siteCandidates(alert.getSiteId(), victim.getId()).stream()
                .map(PeerCandidate::personId).toList();
        int notified = notifyPeers(allIds, alert, victim);
        notifyManagers(alert, victim);
        alert.setPeerEscalatedAt(LocalDateTime.now());
        alertRepo.save(alert);
        log.warn("EmergencyResponse expand alert={} victim={} site-wide peers={}", alert.getId(), victim.getId(), notified);
    }

    /** 경보 해제 시 피재자 폰 파인드미 해제(find_me_stop). DB 미변경(FCM 발송만). */
    public void sendFindMeStop(Person victim) {
        if (victim == null || isBlank(victim.getFcmToken())) return;
        fcm.sendChain(List.of(victim.getFcmToken()), Map.of("kind", "find_me_stop"));
    }

    // ───────── BLE 릴레이 수신(§5.7) ─────────

    /**
     * 제3자 폰 BLE 대리중계 수신 — 피재자의 활성 EMERGENCY 경보가 있으면 릴레이 위치·시각 보강,
     * 없으면 신규 EMERGENCY 경보 생성 + 대응체인 발동. victim당 5분 dedupe(중복 생성 방지).
     */
    @Transactional
    public Map<String, Object> receiveRelay(Long victimPersonId, Double relayLat, Double relayLng) {
        Person victim = persons.findById(victimPersonId)
                .orElseThrow(() -> ApiException.notFound("VICTIM_NOT_FOUND", "피재자를 찾을 수 없습니다"));
        LocalDateTime now = LocalDateTime.now();

        var active = alertRepo.findFirstByPersonIdAndSeverityAndResolvedFalseOrderByCreatedAtDesc(
                victimPersonId, SafetySeverity.EMERGENCY.name());
        if (active.isPresent()) {
            FieldSafetyAlert a = active.get();
            recordRelay(a, relayLat, relayLng, now);
            broadcaster.publishRelay(a);
            return Map.of("ok", true, "alert_id", a.getId(), "relayed", true, "created", false);
        }
        // 활성 경보 없음 — 최근(5분) 릴레이 생성 경보가 있으면 dedupe(재생성·재발동 방지).
        if (alertRepo.existsByPersonIdAndKindAndRelayedAtAfter(
                victimPersonId, "emergency", now.minusMinutes(RELAY_DEDUPE_MIN))) {
            return Map.of("ok", true, "deduped", true, "created", false);
        }
        FieldSafetyAlert a = createRelayAlert(victim, relayLat, relayLng, now);
        broadcaster.publishCreated(a, victim);
        onEmergencyAlert(a, victim);
        return Map.of("ok", true, "alert_id", a.getId(), "created", true);
    }

    // ───────── 내부 배선 ─────────

    private FieldSafetyAlert createRelayAlert(Person victim, Double relayLat, Double relayLng, LocalDateTime now) {
        Ctx ctx = resolveContext(victim.getId());
        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setPersonId(victim.getId());
        a.setWorkPlanId(ctx.workPlanId);
        a.setSiteId(ctx.siteId);
        a.setBpCompanyId(ctx.bpCompanyId);
        a.setKind("emergency");
        a.setLevel("danger");
        a.setSeverity(SafetySeverity.EMERGENCY.name());
        a.setMessage("BLE 구조신호 중계 수신 — 통신 음영(터널·지하) 추정, 즉시 확인하세요");
        a.setLat(relayLat);   // 피재자 GPS 없음 → 중계자 위치가 최선 추정(지도 마커).
        a.setLng(relayLng);
        a.setRelayedAt(now);
        a.setRelayLat(relayLat);
        a.setRelayLng(relayLng);
        alertRepo.save(a);
        return a;
    }

    private void recordRelay(FieldSafetyAlert a, Double relayLat, Double relayLng, LocalDateTime now) {
        a.setRelayedAt(now);
        if (relayLat != null) a.setRelayLat(relayLat);
        if (relayLng != null) a.setRelayLng(relayLng);
        // 피재자 GPS가 없던 경보면 릴레이 위치로 지도 표시 보강.
        if (a.getLat() == null && relayLat != null) a.setLat(relayLat);
        if (a.getLng() == null && relayLng != null) a.setLng(relayLng);
        alertRepo.save(a);
    }

    private void sendFindMe(Person victim, Long alertId) {
        if (isBlank(victim.getFcmToken())) return;
        Map<String, String> d = new java.util.HashMap<>();
        d.put("kind", "find_me");
        d.put("alert_id", String.valueOf(alertId));
        d.put("person_id", String.valueOf(victim.getId()));
        fcm.sendChain(List.of(victim.getFcmToken()), d);
    }

    /** peer_emergency 발송 — 폰이 kind 로 PeerEmergencyActivity 풀스크린. @return 통보 대상 동료 토큰 수(FCM 활성 무관). */
    private int notifyPeers(List<Long> peerIds, FieldSafetyAlert alert, Person victim) {
        if (peerIds.isEmpty()) return 0;
        List<String> tokens = persons.findAllById(peerIds).stream()
                .map(Person::getFcmToken).filter(t -> !isBlank(t)).toList();
        if (tokens.isEmpty()) return 0;
        fcm.sendChain(tokens, peerEmergencyData(alert, victim));
        return tokens.size();
    }

    private Map<String, String> peerEmergencyData(FieldSafetyAlert alert, Person victim) {
        Map<String, String> d = new java.util.HashMap<>();
        d.put("kind", "peer_emergency");
        d.put("alert_id", String.valueOf(alert.getId()));
        d.put("victim_name", victim.getName() != null ? victim.getName() : ("작업자 #" + victim.getId()));
        if (alert.getLat() != null) d.put("lat", String.valueOf(alert.getLat()));
        if (alert.getLng() != null) d.put("lng", String.valueOf(alert.getLng()));
        return d;
    }

    private void notifyManagers(FieldSafetyAlert alert, Person victim) {
        String who = victim.getName() != null ? victim.getName() : ("작업자 #" + victim.getId());
        String title = who + " 긴급 — 동료 무응답";
        String message = who + " 작업자 긴급 상황에 60초간 동료 응답이 없어 현장 전체로 확대했습니다. 즉시 확인하세요.";
        Set<Long> companies = new LinkedHashSet<>();
        if (alert.getBpCompanyId() != null) companies.add(alert.getBpCompanyId());
        if (victim.getSupplierId() != null) companies.add(victim.getSupplierId());
        for (Long companyId : companies) {
            notifications.sendToCompany(companyId, NotificationType.EMERGENCY_NO_RESPONSE,
                    title, message, "SITE", alert.getSiteId(), alert.getSiteId(), "시스템 (긴급 대응체인)");
        }
    }

    /** 같은 현장 열린(미퇴근) 세션 인원 → 후보(피재자 제외, 1인 1행). 계획서 배치 조회로 N+1 회피. */
    private List<PeerCandidate> siteCandidates(Long siteId, Long excludePersonId) {
        if (siteId == null) return List.of();
        List<AttendanceSession> open = attendance.findByCheckOutAtIsNull();
        List<Long> wpIds = open.stream().map(AttendanceSession::getWorkPlanId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, WorkPlan> wpById = new java.util.HashMap<>();
        for (WorkPlan wp : workPlans.findAllById(wpIds)) wpById.put(wp.getId(), wp);

        Map<Long, PeerCandidate> byPerson = new LinkedHashMap<>();   // 다중 세션 시 첫 세션 유지.
        for (AttendanceSession s : open) {
            if (s.getWorkPlanId() == null) continue;
            if (Objects.equals(s.getPersonId(), excludePersonId)) continue;
            WorkPlan wp = wpById.get(s.getWorkPlanId());
            if (wp == null || !Objects.equals(wp.getSiteId(), siteId)) continue;
            byPerson.putIfAbsent(s.getPersonId(),
                    new PeerCandidate(s.getPersonId(), s.getWorkPlanId(), s.getCheckInLat(), s.getCheckInLng()));
        }
        return new ArrayList<>(byPerson.values());
    }

    /** 피재자 현재 컨텍스트(최근 세션 → 계획서 → 현장/BP). 릴레이 신규 경보 권한 필터 캐시용. */
    private Ctx resolveContext(Long personId) {
        Ctx ctx = new Ctx();
        AttendanceSession s = attendance.findFirstByPersonIdOrderByCheckInAtDesc(personId).orElse(null);
        if (s != null && s.getWorkPlanId() != null) {
            ctx.workPlanId = s.getWorkPlanId();
            WorkPlan wp = workPlans.findById(s.getWorkPlanId()).orElse(null);
            if (wp != null) {
                ctx.siteId = wp.getSiteId();
                ctx.bpCompanyId = wp.getBpCompanyId();
            }
        }
        return ctx;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static class Ctx {
        Long workPlanId;
        Long siteId;
        Long bpCompanyId;
    }
}
