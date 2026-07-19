package com.dainon.skep.safety

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * P5-W3 제3자 SOS 대리중계 — 파인드미 BLE 광고 serviceData(8B) 순수 파서.
 * 송출측(FindMeAlarmService)이 ByteBuffer(8, BIG_ENDIAN).putInt(alertId).putInt(personId) 로 만든 payload 를
 * 그대로 되읽는다. RssiMapper 처럼 실기기 없이 경계값 단위테스트 하도록 분리(특허 §5.7).
 */
object SosPayloadParser {
    /** alertId(int32) + personId(int32) = 8바이트. */
    const val PAYLOAD_LEN = 8

    /** 파싱 결과. alertId 는 로컬 자가발동 시 -1 일 수 있다. */
    data class Payload(val alertId: Int, val personId: Int)

    /** serviceData 바이트 → Payload. 길이 부족·null 이면 null(중계 불가). */
    fun parse(bytes: ByteArray?): Payload? {
        if (bytes == null || bytes.size < PAYLOAD_LEN) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val alertId = bb.int
        val personId = bb.int
        return Payload(alertId, personId)
    }
}
