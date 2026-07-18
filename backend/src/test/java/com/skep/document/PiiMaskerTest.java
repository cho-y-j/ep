package com.skep.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PiiMaskerTest {

    @Test
    void maskKoreanResidentNumberInJson() {
        String json = "{\"name\":\"홍길동\",\"rrn\":\"880101-1234567\"}";
        assertEquals("{\"name\":\"홍길동\",\"rrn\":\"880101-1******\"}", PiiMasker.mask(json));
    }

    @Test
    void maskDriverLicenseNumber() {
        String text = "면허번호 12-34-567890-78 입니다";
        assertEquals("면허번호 12-34-******-78 입니다", PiiMasker.mask(text));
    }

    @Test
    void maskBothRrnAndDriverInSameString() {
        String text = "주민 991231-2123456 면허 11-22-333333-44";
        assertEquals("주민 991231-2****** 면허 11-22-******-44", PiiMasker.mask(text));
    }

    @Test
    void noMatchLeavesStringUntouched() {
        // BIZ_NO(사업자등록번호) 마스킹은 커밋 2bfc854 에서 의도적으로 추가됨 → 사업자번호는 마스킹, 차량번호(12가3456)는 미매칭 유지.
        String text = "사업자 123-45-67890 vehicle 12가3456";
        assertEquals("사업자 123-**-***** vehicle 12가3456", PiiMasker.mask(text));
    }

    @Test
    void nullAndEmptyPassthrough() {
        assertNull(PiiMasker.mask(null));
        assertEquals("", PiiMasker.mask(""));
    }
}
