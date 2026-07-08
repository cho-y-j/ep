package com.skep.workplan.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateWorkPlanRequest(
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 150) String title,
        @Size(max = 255) String workLocation,
        String description
) {
}
