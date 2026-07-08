package com.skep.field.dto;

import java.time.LocalDateTime;

public record SafetyAlertRow(
        Long alertId,
        Long workerId,
        String workerName,
        Long siteId,
        String kind,
        Integer hr,
        Integer spo2,
        Double lat,
        Double lng,
        boolean resolved,
        LocalDateTime createdAt
) {
}
