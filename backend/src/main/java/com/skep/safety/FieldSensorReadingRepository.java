package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FieldSensorReadingRepository extends JpaRepository<FieldSensorReading, Long> {

    List<FieldSensorReading> findByPersonIdAndRecordedAtAfterOrderByRecordedAtDesc(Long personId, LocalDateTime since);

    List<FieldSensorReading> findTop50ByPersonIdOrderByRecordedAtDesc(Long personId);
}
