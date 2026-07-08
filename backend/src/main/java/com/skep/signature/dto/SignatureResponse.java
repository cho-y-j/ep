package com.skep.signature.dto;

import com.skep.signature.SignatureRole;
import com.skep.signature.SignatureStatus;
import com.skep.signature.WorksheetSignature;

import java.time.LocalDateTime;
import java.util.Base64;

public record SignatureResponse(
        Long id,
        Long workPlanId,
        SignatureRole role,
        String roleLabel,
        String signerName,
        String signerEmail,
        SignatureStatus status,
        LocalDateTime signedAt,
        LocalDateTime tokenExpiresAt,
        boolean hasSignature,
        String signaturePngBase64
) {
    public static SignatureResponse from(WorksheetSignature s, String roleLabel, boolean includePng) {
        boolean hasPng = s.getSignaturePng() != null && s.getSignaturePng().length > 0;
        String b64 = (includePng && hasPng) ? Base64.getEncoder().encodeToString(s.getSignaturePng()) : null;
        return new SignatureResponse(
                s.getId(), s.getWorkPlanId(), s.getRole(), roleLabel,
                s.getSignerName(), s.getSignerEmail(), s.getStatus(),
                s.getSignedAt(), s.getTokenExpiresAt(), hasPng, b64
        );
    }
}
