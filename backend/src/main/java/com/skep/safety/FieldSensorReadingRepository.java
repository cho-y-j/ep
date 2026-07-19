package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FieldSensorReadingRepository extends JpaRepository<FieldSensorReading, Long> {

    List<FieldSensorReading> findByPersonIdAndRecordedAtAfterOrderByRecordedAtDesc(Long personId, LocalDateTime since);

    List<FieldSensorReading> findTop50ByPersonIdOrderByRecordedAtDesc(Long personId);

    /** P5-W1 2차 판정 평가창(시간 오름차순 — 추세·연속 판정용). */
    List<FieldSensorReading> findByPersonIdAndRecordedAtAfterOrderByRecordedAtAsc(Long personId, LocalDateTime since);

    /** P5-W1 관제 스파크라인 — 여러 작업자 최근 readings 배치 조회. */
    List<FieldSensorReading> findByPersonIdInAndRecordedAtAfterOrderByRecordedAtAsc(Collection<Long> personIds, LocalDateTime since);

    /** P5-W1 주기 재학습 크론 — 최근 window 에 readings 있는 작업자 id. */
    @Query("select distinct r.personId from FieldSensorReading r where r.recordedAt >= :since")
    List<Long> findDistinctPersonIdsByRecordedAtAfter(LocalDateTime since);
}
