package com.example.verifyapi.rims;

import com.example.verifyapi.rims.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RIMS 운전면허 검증 서비스
 * - 단건/배치 검증 처리
 * - 결과 판정 (f_rtn_code == "00" → VALID)
 */
@Service
public class RimsVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RimsVerificationService.class);
    private static final String SINGLE_VERIFY_ENDPOINT = "/licenseVerification";
    private static final String BATCH_VERIFY_ENDPOINT = "/licenseVerificationBatch";
    private static final String SUCCESS_CODE = "00";
    private static final String[] RESULT_LIST_KEYS = {"resultList", "results", "data", "list"};

    /** 차량번호 미입력 시 RIMS 기본값 */
    private static final String DEFAULT_VEHICLE_REG_NO = "99임9999";

    /** 날짜 포맷 (YYYYMMDD) */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RimsClient rimsClient;

    public RimsVerificationService(RimsClient rimsClient) {
        this.rimsClient = rimsClient;
    }

    /**
     * 단건 운전면허 검증
     */
    public RimsVerifyResponse verifySingle(RimsLicenseRequest request) {
        try {
            // 요청 데이터 전처리
            preprocessSingleRequest(request);

            log.info("RIMS request after preprocess: licenseNo={}, name={}, typeCode={}, fromDate={}, toDate={}, vehicleRegNo={}",
                request.getLicenseNo(), request.getResidentName(), request.getLicenseConditionCode(),
                request.getFromDate(), request.getToDate(), request.getVehicleRegNo());

            String responseBody = rimsClient.post(SINGLE_VERIFY_ENDPOINT, request);
            Map<String, Object> responseMap = rimsClient.parseResponse(responseBody);

            return evaluateSingleResult(responseMap);

        } catch (RimsClient.RimsClientException e) {
            log.error("RIMS single verification failed: {}", e.getReasonCode(), e);
            RimsVerifyResponse response = RimsVerifyResponse.unknown(e.getReasonCode());
            return response;

        } catch (RimsTokenService.RimsTokenException e) {
            log.error("RIMS token error during single verification", e);
            return RimsVerifyResponse.unknown("RIMS_TOKEN_ERROR");

        } catch (RimsCrypto.RimsCryptoException e) {
            log.error("RIMS encryption error during single verification", e);
            return RimsVerifyResponse.unknown("RIMS_ENCRYPTION_ERROR");

        } catch (Exception e) {
            log.error("Unexpected error during RIMS single verification", e);
            return RimsVerifyResponse.unknown("RIMS_UNKNOWN_ERROR");
        }
    }

    /**
     * 배치 운전면허 검증
     */
    public RimsBatchVerifyResponse verifyBatch(RimsLicenseBatchRequest request) {
        RimsBatchVerifyResponse response = new RimsBatchVerifyResponse();
        List<RimsBatchVerifyItem> results = new ArrayList<>();

        int validCount = 0;
        int invalidCount = 0;
        int unknownCount = 0;

        try {
            // 요청 데이터 전처리 (배치 내 각 항목)
            preprocessBatchRequest(request);

            String responseBody = rimsClient.post(BATCH_VERIFY_ENDPOINT, request);
            Map<String, Object> responseMap = rimsClient.parseResponse(responseBody);

            // 배치 응답에서 결과 목록 추출 (여러 후보 키 조회)
            List<?> resultListObj = findResultList(responseMap);
            if (resultListObj != null) {
                for (int i = 0; i < resultListObj.size(); i++) {
                    Object itemObj = resultListObj.get(i);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemResult = (itemObj instanceof Map) ? (Map<String, Object>) itemObj : Map.of();

                    RimsLicenseBatchItem requestItem = i < request.getRequestList().size()
                            ? request.getRequestList().get(i)
                            : null;

                    // 응답에 면허번호/성명이 있으면 우선 사용
                    String licenseNo = extractStringFirstOf(itemResult, "f_license_no", "f_licenseNo");
                    if (licenseNo == null || licenseNo.isBlank()) {
                        licenseNo = requestItem != null ? requestItem.getLicenseNo() : "";
                    }
                    String residentName = extractStringFirstOf(itemResult, "f_resident_name", "f_residentName");
                    if (residentName == null || residentName.isBlank()) {
                        residentName = requestItem != null ? requestItem.getResidentName() : "";
                    }

                    RimsBatchVerifyItem item = evaluateBatchItemResult(i, licenseNo, residentName, itemResult);
                    results.add(item);

                    switch (item.getResult()) {
                        case "VALID" -> validCount++;
                        case "INVALID" -> invalidCount++;
                        default -> unknownCount++;
                    }
                }
            } else {
                // 결과 목록을 찾을 수 없는 경우 전체 UNKNOWN 처리
                log.warn("RIMS batch response missing result list (tried keys: {})", String.join(", ", RESULT_LIST_KEYS));
                for (int i = 0; i < request.getRequestList().size(); i++) {
                    RimsLicenseBatchItem requestItem = request.getRequestList().get(i);
                    RimsBatchVerifyItem item = RimsBatchVerifyItem.unknown(
                            i, requestItem.getLicenseNo(), requestItem.getResidentName(), "RIMS_INVALID_RESPONSE");
                    results.add(item);
                    unknownCount++;
                }
            }

        } catch (RimsClient.RimsClientException e) {
            log.error("RIMS batch verification failed: {}", e.getReasonCode(), e);
            // 전체 요청 실패 시 모든 항목을 UNKNOWN으로 처리
            for (int i = 0; i < request.getRequestList().size(); i++) {
                RimsLicenseBatchItem requestItem = request.getRequestList().get(i);
                RimsBatchVerifyItem item = RimsBatchVerifyItem.unknown(
                        i, requestItem.getLicenseNo(), requestItem.getResidentName(), e.getReasonCode());
                results.add(item);
                unknownCount++;
            }

        } catch (RimsTokenService.RimsTokenException e) {
            log.error("RIMS token error during batch verification", e);
            for (int i = 0; i < request.getRequestList().size(); i++) {
                RimsLicenseBatchItem requestItem = request.getRequestList().get(i);
                RimsBatchVerifyItem item = RimsBatchVerifyItem.unknown(
                        i, requestItem.getLicenseNo(), requestItem.getResidentName(), "RIMS_TOKEN_ERROR");
                results.add(item);
                unknownCount++;
            }

        } catch (RimsCrypto.RimsCryptoException e) {
            log.error("RIMS encryption error during batch verification", e);
            for (int i = 0; i < request.getRequestList().size(); i++) {
                RimsLicenseBatchItem requestItem = request.getRequestList().get(i);
                RimsBatchVerifyItem item = RimsBatchVerifyItem.unknown(
                        i, requestItem.getLicenseNo(), requestItem.getResidentName(), "RIMS_ENCRYPTION_ERROR");
                results.add(item);
                unknownCount++;
            }

        } catch (Exception e) {
            log.error("Unexpected error during RIMS batch verification", e);
            for (int i = 0; i < request.getRequestList().size(); i++) {
                RimsLicenseBatchItem requestItem = request.getRequestList().get(i);
                RimsBatchVerifyItem item = RimsBatchVerifyItem.unknown(
                        i, requestItem.getLicenseNo(), requestItem.getResidentName(), "RIMS_UNKNOWN_ERROR");
                results.add(item);
                unknownCount++;
            }
        }

        response.setTotalCount(results.size());
        response.setValidCount(validCount);
        response.setInvalidCount(invalidCount);
        response.setUnknownCount(unknownCount);
        response.setResults(results);

        return response;
    }

    private RimsVerifyResponse evaluateSingleResult(Map<String, Object> responseMap) {
        // RIMS 응답 구조: { header: {...}, body: { f_rtn_code: "00", ... } }
        log.info("RIMS evaluateSingleResult: responseMap={}", responseMap);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (responseMap.get("body") instanceof Map)
                ? (Map<String, Object>) responseMap.get("body")
                : null;

        if (body == null) {
            log.warn("RIMS response missing 'body' field");
            RimsVerifyResponse response = RimsVerifyResponse.unknown("RIMS_INVALID_RESPONSE");
            response.setRaw(responseMap);
            return response;
        }

        String rtnCode = extractString(body, "f_rtn_code");
        String vhclIdntyCd = extractString(body, "vhcl_idnty_cd");

        if (SUCCESS_CODE.equals(rtnCode)) {
            RimsVerifyResponse response = RimsVerifyResponse.valid();
            response.setRaw(responseMap);
            return response;
        } else if ("01".equals(rtnCode) && "2".equals(vhclIdntyCd)) {
            // f_rtn_code=01 + vhcl_idnty_cd=2 → 면허는 유효하나 차량만 부적격
            // 면허 진위확인 목적이므로 VALID로 판정
            log.info("RIMS: license VALID (vehicle check skipped, vhcl_idnty_cd=2)");
            RimsVerifyResponse response = RimsVerifyResponse.valid();
            response.setRaw(responseMap);
            return response;
        } else if (rtnCode != null && !rtnCode.isBlank()) {
            // f_rtn_code != "00" → INVALID (부적격)
            RimsVerifyResponse response = RimsVerifyResponse.invalid("RIMS_CODE_" + rtnCode);
            response.setRaw(responseMap);
            return response;
        } else {
            RimsVerifyResponse response = RimsVerifyResponse.unknown("RIMS_INVALID_RESPONSE");
            response.setRaw(responseMap);
            return response;
        }
    }

    private RimsBatchVerifyItem evaluateBatchItemResult(int index, String licenseNo,
                                                         String residentName, Map<String, Object> itemResult) {
        String rtnCode = extractString(itemResult, "f_rtn_code");

        RimsBatchVerifyItem item;
        if (SUCCESS_CODE.equals(rtnCode)) {
            item = RimsBatchVerifyItem.valid(index, licenseNo, residentName);
        } else if (rtnCode != null && !rtnCode.isBlank()) {
            item = RimsBatchVerifyItem.invalid(index, licenseNo, residentName, "RIMS_CODE_" + rtnCode);
        } else {
            item = RimsBatchVerifyItem.unknown(index, licenseNo, residentName, "RIMS_INVALID_RESPONSE");
        }
        item.setRaw(itemResult);
        return item;
    }

    private List<?> findResultList(Map<String, Object> responseMap) {
        for (String key : RESULT_LIST_KEYS) {
            Object obj = responseMap.get(key);
            if (obj instanceof List) {
                return (List<?>) obj;
            }
        }
        return null;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private String extractStringFirstOf(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = extractString(map, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // ========== 요청 데이터 전처리 ==========

    /**
     * 단건 요청 전처리
     * - 면허번호 정규화 (지역명→코드)
     * - 날짜 기본값 설정 (오늘)
     * - 차량번호 기본값 설정
     */
    private void preprocessSingleRequest(RimsLicenseRequest request) {
        // 1. 면허번호 정규화 (지역명 → 지역코드)
        String normalizedLicenseNo = RimsLicenseUtils.normalizeLicenseNo(request.getLicenseNo());
        request.setLicenseNo(normalizedLicenseNo);

        // 2. 날짜 기본값 설정 (오늘, KST 기준)
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_FORMAT);
        if (request.getFromDate() == null || request.getFromDate().isBlank()) {
            request.setFromDate(today);
        }
        if (request.getToDate() == null || request.getToDate().isBlank()) {
            request.setToDate(today);
        }

        // 3. 차량번호 기본값 설정
        if (request.getVehicleRegNo() == null || request.getVehicleRegNo().isBlank()) {
            request.setVehicleRegNo(DEFAULT_VEHICLE_REG_NO);
        }
    }

    /**
     * 배치 요청 전처리
     * - 각 항목에 대해 면허번호, 날짜, 차량번호 전처리
     */
    private void preprocessBatchRequest(RimsLicenseBatchRequest request) {
        if (request.getRequestList() == null) {
            return;
        }

        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_FORMAT);

        for (RimsLicenseBatchItem item : request.getRequestList()) {
            // 1. 면허번호 정규화
            String normalizedLicenseNo = RimsLicenseUtils.normalizeLicenseNo(item.getLicenseNo());
            item.setLicenseNo(normalizedLicenseNo);

            // 2. 날짜 기본값 설정
            if (item.getFromDate() == null || item.getFromDate().isBlank()) {
                item.setFromDate(today);
            }
            if (item.getToDate() == null || item.getToDate().isBlank()) {
                item.setToDate(today);
            }

            // 3. 차량번호 기본값 설정
            if (item.getVehicleRegNo() == null || item.getVehicleRegNo().isBlank()) {
                item.setVehicleRegNo(DEFAULT_VEHICLE_REG_NO);
            }
        }
    }
}
