package com.skep.field.dto;

import java.time.LocalDateTime;

public record CheckInResponse(
        LocalDateTime checkedInAt
) {
}
