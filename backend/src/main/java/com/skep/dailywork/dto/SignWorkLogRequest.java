package com.skep.dailywork.dto;

import jakarta.validation.constraints.NotBlank;

/** BP 인앱 서명 — 캔버스 PNG(base64, data URL 접두 허용). */
public record SignWorkLogRequest(
        @NotBlank String signaturePngBase64
) {}
