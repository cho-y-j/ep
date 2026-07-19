package com.dainon.skep.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SosPayloadParserTest {

    /** 송출측(FindMeAlarmService)과 동일한 인코딩으로 payload 생성. */
    private fun encode(alertId: Int, personId: Int): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(alertId).putInt(personId).array()

    @Test fun roundtrip_typical() {
        val p = SosPayloadParser.parse(encode(1234, 5678))!!
        assertEquals(1234, p.alertId)
        assertEquals(5678, p.personId)
    }

    @Test fun local_self_trigger_alertId_minus_one() {
        val p = SosPayloadParser.parse(encode(-1, 42))!!
        assertEquals(-1, p.alertId)
        assertEquals(42, p.personId)
    }

    @Test fun big_endian_order() {
        // alertId=1 → 00 00 00 01, personId=2 → 00 00 00 02.
        val bytes = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 2)
        val p = SosPayloadParser.parse(bytes)!!
        assertEquals(1, p.alertId)
        assertEquals(2, p.personId)
    }

    @Test fun large_person_id() {
        val p = SosPayloadParser.parse(encode(-1, 2_000_000_111))!!
        assertEquals(2_000_000_111, p.personId)
    }

    @Test fun null_returns_null() {
        assertNull(SosPayloadParser.parse(null))
    }

    @Test fun too_short_returns_null() {
        assertNull(SosPayloadParser.parse(ByteArray(SosPayloadParser.PAYLOAD_LEN - 1)))
    }

    @Test fun empty_returns_null() {
        assertNull(SosPayloadParser.parse(ByteArray(0)))
    }

    @Test fun exact_length_parses() {
        assertEquals(0, SosPayloadParser.parse(ByteArray(SosPayloadParser.PAYLOAD_LEN))!!.personId)
    }
}
