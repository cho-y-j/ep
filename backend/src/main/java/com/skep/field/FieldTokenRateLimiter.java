package com.skep.field;

import com.skep.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * X-Field-Token 인증 경로 공용 레이트리밋 — IP별 고정윈도(1분/10회).
 * 저엔트로피 출근코드 브루트포스를 authenticate 계열 전 엔드포인트에서 차단하기 위해
 * FieldAuthController/FieldSafetyController/EquipmentInspectionController 가 공유한다.
 */
@Component
public class FieldTokenRateLimiter {

    private final Map<String, long[]> buckets = new ConcurrentHashMap<>();

    /** slot = {윈도시작 millis, count}. */
    public void check(HttpServletRequest request) {
        // forward-headers-strategy=framework 이므로 getRemoteAddr 가 신뢰 프록시 기준으로 해석됨.
        // 수동 XFF 파싱은 공격자가 헤더만 바꿔 매 요청 새 버킷을 만들 수 있어 쓰지 않는다.
        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        long[] slot = buckets.compute(ip, (k, v) -> {
            if (v == null || now - v[0] >= 60_000L) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        if (slot[1] > 10) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도하세요");
        }
    }
}
