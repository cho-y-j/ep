package com.skep.field.dto;

import java.time.LocalDateTime;

public record AttendanceRow(
        Long attendanceId,
        Long workerId,
        String workerName,
        Long siteId,
        LocalDateTime checkedInAt
) {
}
