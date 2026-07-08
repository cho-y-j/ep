package com.skep.assignment.dto;

import jakarta.validation.constraints.Size;

/** 장비/인원을 현장에서 해제할 때 보낸다. body 없이도 호출 가능. */
public record ReleaseRequest(
        @Size(max = 255) String releaseReason
) {
}
