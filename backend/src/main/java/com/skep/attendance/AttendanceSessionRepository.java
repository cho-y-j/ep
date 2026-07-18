package com.skep.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(Long personId, Long workPlanId);

    /** 현재 출근 중(미퇴근) 세션 전체 — 휴식 알림 스케줄러용. */
    List<AttendanceSession> findByCheckOutAtIsNull();

    /** 가장 최근 세션 1건 — 워치 sensor/emergency 컨텍스트용 (전체 이력 로드 회피). */
    Optional<AttendanceSession> findFirstByPersonIdOrderByCheckInAtDesc(Long personId);

    /** 해당 작업계획서의 가장 최근 퇴근 완료 세션 1건. */
    Optional<AttendanceSession> findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNotNullOrderByCheckInAtDesc(
            Long personId, Long workPlanId);

    /** 특정 시각 이후 출근한 세션 전체 — 주변 호출 대상 산출용. */
    List<AttendanceSession> findByCheckInAtGreaterThanEqual(LocalDateTime since);

    List<AttendanceSession> findByPersonIdInAndCheckInAtGreaterThanEqualOrderByCheckInAtDesc(
            java.util.Collection<Long> personIds, LocalDateTime since);

    /** 안전 상황판 — 현장 작업계획서들의 오늘 출근 세션(작업자 위치 마커). */
    List<AttendanceSession> findByWorkPlanIdInAndCheckInAtGreaterThanEqual(
            java.util.Collection<Long> workPlanIds, LocalDateTime since);

    List<AttendanceSession> findByPersonIdOrderByCheckInAtDesc(Long personId);

    List<AttendanceSession> findByWorkPlanIdOrderByCheckInAtDesc(Long workPlanId);

    boolean existsByPersonIdAndCheckInPhotoKey(Long personId, String key);
    boolean existsByPersonIdAndCheckOutPhotoKey(Long personId, String key);
}
