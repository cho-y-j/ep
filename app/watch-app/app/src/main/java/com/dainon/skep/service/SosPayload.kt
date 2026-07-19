package com.dainon.skep.service

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * P5-W3 통신불능 폴백 — 워치 BLE SOS 광고 serviceData(8B) 순수 인코더(특허 §5.7).
 * 폰 SosPayloadParser 와 동일 배치: alertId(int32 BE) + personId(int32 BE) = 8바이트.
 * 워치는 alertId 를 모른다(서버가 발급) → -1 로 고정 송출.
 * 실기기 없이 경계값 단위테스트 하도록 순수 함수로 분리(폰 SosPayloadParser 와 라운드트립 정합).
 */
object SosPayload {
    /** BLE SOS 광고 service UUID — 폰 FindMeAlarmService.SERVICE_UUID 와 동일(공유 계약). */
    const val SERVICE_UUID = "8f7e0001-a2b3-4c5d-9e8f-102030405060"

    /** alertId(int32) + personId(int32) = 8바이트. */
    const val PAYLOAD_LEN = 8

    /** 워치는 alertId 미보유 → -1(로컬 자가발동 sentinel). 폰 파서도 -1 을 로컬 발동으로 해석. */
    const val WATCH_ALERT_ID = -1

    /** alertId + personId → 8B(BIG_ENDIAN). 폰 SosPayloadParser.parse 로 그대로 되읽힌다. */
    fun encode(alertId: Int, personId: Int): ByteArray =
        ByteBuffer.allocate(PAYLOAD_LEN).order(ByteOrder.BIG_ENDIAN)
            .putInt(alertId).putInt(personId).array()
}
