package com.skep.health;

import com.skep.safety.SiteSafetySettings;

/**
 * P5-W4 1겹 — 현장 혈압 임계값. 설정 행이 없으면 기본값(160/100 주의·180/110 차단권고).
 * 법정 기준이 아니므로 완화 금지 가드 없음(SafetyThresholds 와 별개, 자유 설정).
 */
public record BpThresholds(int cautionSys, int cautionDia, int blockSys, int blockDia) {

    public static final int DEFAULT_CAUTION_SYS = 160;
    public static final int DEFAULT_CAUTION_DIA = 100;
    public static final int DEFAULT_BLOCK_SYS = 180;
    public static final int DEFAULT_BLOCK_DIA = 110;

    public static BpThresholds defaults() {
        return new BpThresholds(DEFAULT_CAUTION_SYS, DEFAULT_CAUTION_DIA, DEFAULT_BLOCK_SYS, DEFAULT_BLOCK_DIA);
    }

    /** 설정 행 없는 현장 = 기본값. */
    public static BpThresholds from(SiteSafetySettings s) {
        if (s == null) return defaults();
        return new BpThresholds(s.getBpCautionSys(), s.getBpCautionDia(), s.getBpBlockSys(), s.getBpBlockDia());
    }
}
