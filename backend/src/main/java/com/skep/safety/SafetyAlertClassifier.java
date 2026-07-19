package com.skep.safety;

import com.skep.weather.HeatStage;

/**
 * S5' 안전알림 3등급 분류 + TTS 정형 문구.
 * 발송 지점(SafetyAlertBroadcaster)·에스컬레이션 재알림(SafetyAckEscalationScheduler)이 공유.
 * TTS 문구는 짧은 정형(폰앱이 읽어줌) — "휴식 시간입니다. 20분간 쉬세요" 등.
 */
public final class SafetyAlertClassifier {

    private SafetyAlertClassifier() {}

    /** 폭염 단계 → 등급. 38℃(EXTREME=작업중지)=긴급, 그 외 폭염·휴식(31/33/35·근로기준법)=주의. */
    public static SafetySeverity heatSeverity(HeatStage stage) {
        return stage == HeatStage.EXTREME ? SafetySeverity.EMERGENCY : SafetySeverity.CAUTION;
    }

    /** kind·등급 → 짧은 정형 TTS 문구(폰앱 읽어주기). */
    public static String tts(String kind, SafetySeverity severity) {
        return switch (kind == null ? "" : kind) {
            case "wind_stop" -> "강풍 작업 중지입니다. 즉시 안전한 곳으로 이동하세요.";
            case "heat" -> severity == SafetySeverity.EMERGENCY
                    ? "폭염 작업 중지입니다. 즉시 그늘에서 쉬세요."
                    : "폭염 주의입니다. 물을 마시고 그늘에서 쉬세요.";
            case "rest" -> "휴식 시간입니다. 20분간 쉬세요.";
            case "heat_risk" -> "체온이 오르고 있습니다. 그늘에서 휴식하세요.";
            case "vital_anomaly" -> "몸 상태를 확인하세요. 괜찮으면 확인을 눌러주세요.";
            case "watch_offline" -> "워치 신호를 확인하세요. 정상이면 확인을 눌러주세요.";
            case "overwork" -> "최근 근무가 많습니다. 충분히 휴식하세요.";
            case "emergency", "fall", "fall_detected" -> "긴급 상황입니다. 즉시 확인하세요.";
            default -> "안전 알림입니다. 확인하세요.";
        };
    }
}
