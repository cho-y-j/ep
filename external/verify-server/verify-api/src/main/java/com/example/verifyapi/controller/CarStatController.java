package com.example.verifyapi.controller;

import com.example.verifyapi.dto.carstat.CarInspectionStatRequest;
import com.example.verifyapi.dto.carstat.CarPerformanceStatRequest;
import com.example.verifyapi.dto.carstat.CarStatResponse;
import com.example.verifyapi.service.CarStatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자동차 통계 조회 컨트롤러 (verify-api 내부용)
 */
@RestController
@RequestMapping("/statistics")
public class CarStatController {

    private final CarStatService carStatService;

    public CarStatController(CarStatService carStatService) {
        this.carStatService = carStatService;
    }

    /**
     * 자동차 성능점검 통계 조회
     */
    @PostMapping("/car-performance")
    public ResponseEntity<CarStatResponse> getCarPerformanceStats(
            @Valid @RequestBody CarPerformanceStatRequest request) {
        CarStatResponse response = carStatService.getPerformanceStats(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 자동차 검사정보 통계 조회
     */
    @PostMapping("/car-inspection")
    public ResponseEntity<CarStatResponse> getCarInspectionStats(
            @Valid @RequestBody CarInspectionStatRequest request) {
        CarStatResponse response = carStatService.getInspectionStats(request);
        return ResponseEntity.ok(response);
    }
}
