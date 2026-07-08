package com.example.verifyapi.service;

import com.example.verifyapi.dto.carstat.CarInspectionStatRequest;
import com.example.verifyapi.dto.carstat.CarPerformanceStatRequest;
import com.example.verifyapi.dto.carstat.CarStatResponse;
import com.example.verifyapi.provider.CarStatProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 자동차 통계 서비스
 * - 자동차 성능점검 통계 조회
 * - 자동차 검사정보 통계 조회
 */
@Service
public class CarStatService {

    private static final Logger log = LoggerFactory.getLogger(CarStatService.class);

    private final CarStatProvider carStatProvider;

    public CarStatService(CarStatProvider carStatProvider) {
        this.carStatProvider = carStatProvider;
    }

    /**
     * 자동차 성능점검 통계 조회
     */
    public CarStatResponse getPerformanceStats(CarPerformanceStatRequest request) {
        log.info("자동차 성능점검 통계 조회 - 년도: {}, 월: {}, 차종: {}, 지역: {}, 모델년도: {}",
                request.getRegistYy(), request.getRegistMt(), request.getVhctyAsortCode(),
                request.getRegistGrcCode(), request.getPrye());

        return carStatProvider.getPerformanceStats(request);
    }

    /**
     * 자동차 검사정보 통계 조회
     */
    public CarStatResponse getInspectionStats(CarInspectionStatRequest request) {
        log.info("자동차 검사정보 통계 조회 - 시작일: {}, 종료일: {}, 용도: {}, 차종: {}",
                request.getBgnde(), request.getEndde(), request.getPrposSeNm(), request.getVhctyAsortNm());

        return carStatProvider.getInspectionStats(request);
    }
}
