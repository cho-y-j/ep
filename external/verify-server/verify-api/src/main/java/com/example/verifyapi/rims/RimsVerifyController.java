package com.example.verifyapi.rims;

import com.example.verifyapi.rims.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RIMS 운전면허 검증 API 컨트롤러
 * - POST /verify/rims/license (단건)
 * - POST /verify/rims/license/batch (배치)
 */
@RestController
@RequestMapping("/verify/rims")
public class RimsVerifyController {

    private final RimsVerificationService verificationService;

    public RimsVerifyController(RimsVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * 운전면허 단건 검증
     */
    @PostMapping("/license")
    public ResponseEntity<RimsVerifyResponse> verifyLicense(
            @Valid @RequestBody RimsLicenseRequest request) {
        RimsVerifyResponse response = verificationService.verifySingle(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 운전면허 배치 검증
     */
    @PostMapping("/license/batch")
    public ResponseEntity<RimsBatchVerifyResponse> verifyLicenseBatch(
            @Valid @RequestBody RimsLicenseBatchRequest request) {
        RimsBatchVerifyResponse response = verificationService.verifyBatch(request);
        return ResponseEntity.ok(response);
    }
}
