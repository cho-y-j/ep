package com.skep.field.dto;

/** sent: FCM 발송 시도한 대상 수 (fcm_token 보유 작업자). */
public record AnnouncementResponse(
        int sent
) {
}
