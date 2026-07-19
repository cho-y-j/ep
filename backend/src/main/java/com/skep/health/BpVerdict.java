package com.skep.health;

/**
 * P5-W4 1겹 — 혈압 체크인 판정(서버 계산). 수축기/이완기 중 더 높은 등급을 채택.
 * BLOCK 이어도 출근 하드차단은 없음(권고+관리자 통보) — 판정은 등급만 산출.
 */
public enum BpVerdict {
    OK, CAUTION, BLOCK;

    /** 경계 포함(>=): 임계와 같으면 그 등급으로 상향. */
    public static BpVerdict evaluate(int sys, int dia, BpThresholds t) {
        if (sys >= t.blockSys() || dia >= t.blockDia()) return BLOCK;
        if (sys >= t.cautionSys() || dia >= t.cautionDia()) return CAUTION;
        return OK;
    }
}
