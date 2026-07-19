package com.skep.safety;

import com.skep.weather.HeatStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S5' 등급 매핑 + TTS 정형 문구 순수 판정. */
class SafetyAlertClassifierTest {

    @Test
    void heatSeverityMapping() {
        // 38℃(EXTREME=작업중지)만 긴급, 나머지 폭염 단계·비폭염(휴식)은 주의.
        assertEquals(SafetySeverity.EMERGENCY, SafetyAlertClassifier.heatSeverity(HeatStage.EXTREME));
        assertEquals(SafetySeverity.CAUTION, SafetyAlertClassifier.heatSeverity(HeatStage.DANGER));
        assertEquals(SafetySeverity.CAUTION, SafetyAlertClassifier.heatSeverity(HeatStage.WARNING));
        assertEquals(SafetySeverity.CAUTION, SafetyAlertClassifier.heatSeverity(HeatStage.CAUTION));
        assertEquals(SafetySeverity.CAUTION, SafetyAlertClassifier.heatSeverity(HeatStage.NONE));
    }

    @Test
    void ackRequiredBySeverity() {
        assertTrue(SafetySeverity.EMERGENCY.ackRequired());
        assertTrue(SafetySeverity.CAUTION.ackRequired());
        assertFalse(SafetySeverity.NORMAL.ackRequired());
    }

    @Test
    void severityOfParsesNullAndUnknownAsNormal() {
        assertEquals(SafetySeverity.NORMAL, SafetySeverity.of(null));
        assertEquals(SafetySeverity.NORMAL, SafetySeverity.of("garbage"));
        assertEquals(SafetySeverity.EMERGENCY, SafetySeverity.of("EMERGENCY"));
        assertEquals(SafetySeverity.CAUTION, SafetySeverity.of("CAUTION"));
    }

    @Test
    void ttsPhrasesAreShortAndFormatted() {
        assertEquals("강풍 작업 중지입니다. 즉시 안전한 곳으로 이동하세요.",
                SafetyAlertClassifier.tts("wind_stop", SafetySeverity.EMERGENCY));
        assertEquals("휴식 시간입니다. 20분간 쉬세요.",
                SafetyAlertClassifier.tts("rest", SafetySeverity.CAUTION));
        // 폭염은 등급에 따라 문구 분기.
        assertEquals("폭염 작업 중지입니다. 즉시 그늘에서 쉬세요.",
                SafetyAlertClassifier.tts("heat", SafetySeverity.EMERGENCY));
        assertEquals("폭염 주의입니다. 물을 마시고 그늘에서 쉬세요.",
                SafetyAlertClassifier.tts("heat", SafetySeverity.CAUTION));
    }
}
