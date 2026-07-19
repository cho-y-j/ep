package com.dainon.skep.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SosPayload 인코더 유닛테스트 — 폰 SosPayloadParser 와 라운드트립 정합(특허 §5.7).
 * 폰이 되읽는 방식(ByteBuffer BIG_ENDIAN, alertId 먼저 personId)으로 디코드해 검증한다.
 */
class SosPayloadTest {

    /** 폰 SosPayloadParser.parse 와 동일한 디코딩(BIG_ENDIAN, int alertId, int personId). */
    private fun decode(bytes: ByteArray): Pair<Int, Int> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return bb.int to bb.int
    }

    @Test fun length_is_8() {
        assertEquals(8, SosPayload.PAYLOAD_LEN)
        assertEquals(SosPayload.PAYLOAD_LEN, SosPayload.encode(-1, 42).size)
    }

    @Test fun roundtrip_typical() {
        val (alertId, personId) = decode(SosPayload.encode(1234, 5678))
        assertEquals(1234, alertId)
        assertEquals(5678, personId)
    }

    @Test fun watch_alertId_is_minus_one() {
        assertEquals(-1, SosPayload.WATCH_ALERT_ID)
        val (alertId, personId) = decode(SosPayload.encode(SosPayload.WATCH_ALERT_ID, 42))
        assertEquals(-1, alertId)
        assertEquals(42, personId)
    }

    @Test fun big_endian_layout() {
        // alertId=-1 → FF FF FF FF, personId=2 → 00 00 00 02.
        val bytes = SosPayload.encode(-1, 2)
        val expected = byteArrayOf(-1, -1, -1, -1, 0, 0, 0, 2)
        assertEquals(expected.toList(), bytes.toList())
    }

    @Test fun personId_zero_boundary() {
        val (_, personId) = decode(SosPayload.encode(-1, 0))
        assertEquals(0, personId)
    }

    @Test fun large_person_id() {
        val (_, personId) = decode(SosPayload.encode(-1, 2_000_000_111))
        assertEquals(2_000_000_111, personId)
    }
}
