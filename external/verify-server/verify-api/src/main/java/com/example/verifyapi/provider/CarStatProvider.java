package com.example.verifyapi.provider;

import com.example.verifyapi.dto.carstat.CarInspectionStatRequest;
import com.example.verifyapi.dto.carstat.CarPerformanceStatRequest;
import com.example.verifyapi.dto.carstat.CarStatResponse;
import com.example.verifyapi.exception.VerifyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 자동차 통계 API Provider
 * - 자동차 성능점검 통계 조회
 * - 자동차 검사정보 통계 조회
 */
@Component
public class CarStatProvider {

    private static final Logger log = LoggerFactory.getLogger(CarStatProvider.class);

    private static final String PERFORMANCE_API_URL = "http://apis.data.go.kr/B553881/prfomncChckInfoService/prfomncChckInfoService";
    private static final String INSPECTION_API_URL = "http://apis.data.go.kr/B553881/insptInfoService/getInsptInfo";
    private static final String PROVIDER_NAME = "TS_CAR_API";

    private final RestTemplate restTemplate;

    @Value("${TS_CAR_API_KEY:}")
    private String tsCarApiKey;

    public CarStatProvider(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .additionalMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .build();

        this.restTemplate.getMessageConverters().forEach(converter -> {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setSupportedMediaTypes(
                        Collections.singletonList(MediaType.ALL));
            }
        });
    }

    /**
     * 자동차 성능점검 통계 조회
     */
    public CarStatResponse getPerformanceStats(CarPerformanceStatRequest request) {
        String verifiedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (tsCarApiKey == null || tsCarApiKey.isBlank()) {
            throw new VerifyException("CONFIG_ERROR", "TS_CAR_API_KEY가 설정되지 않았습니다", 500);
        }

        try {
            URI uri = buildPerformanceUri(request);
            log.debug("자동차 성능점검 통계 API 호출: {}", uri);

            String xmlResponse = restTemplate.getForObject(uri, String.class);
            return parseXmlResponse(xmlResponse, verifiedAt);

        } catch (ResourceAccessException e) {
            log.error("자동차 성능점검 API 타임아웃: {}", e.getMessage());
            CarStatResponse response = CarStatResponse.unknown("TIMEOUT", "API 응답 시간 초과");
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (RestClientException e) {
            log.error("자동차 성능점검 API 통신 오류: {}", e.getMessage());
            CarStatResponse response = CarStatResponse.unknown("UPSTREAM_ERROR", "외부 API 통신 오류");
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("자동차 성능점검 API 처리 오류: {}", e.getMessage(), e);
            CarStatResponse response = CarStatResponse.unknown("PARSE_ERROR", "응답 파싱 오류");
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            return response;
        }
    }

    /**
     * 자동차 검사정보 통계 조회
     */
    public CarStatResponse getInspectionStats(CarInspectionStatRequest request) {
        String verifiedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (tsCarApiKey == null || tsCarApiKey.isBlank()) {
            throw new VerifyException("CONFIG_ERROR", "TS_CAR_API_KEY가 설정되지 않았습니다", 500);
        }

        try {
            URI uri = buildInspectionUri(request);
            log.debug("자동차 검사정보 통계 API 호출: {}", uri);

            String xmlResponse = restTemplate.getForObject(uri, String.class);
            return parseXmlResponse(xmlResponse, verifiedAt);

        } catch (ResourceAccessException e) {
            log.error("자동차 검사정보 API 타임아웃: {}", e.getMessage());
            CarStatResponse response = CarStatResponse.unknown("TIMEOUT", "API 응답 시간 초과");
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (RestClientException e) {
            log.error("자동차 검사정보 API 통신 오류: {}", e.getMessage());
            CarStatResponse response = CarStatResponse.unknown("UPSTREAM_ERROR", "외부 API 통신 오류");
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("자동차 검사정보 API 처리 오류: {}", e.getMessage(), e);
            CarStatResponse response = CarStatResponse.unknown("PARSE_ERROR", "응답 파싱 오류");
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            return response;
        }
    }

    /**
     * 자동차 성능점검 통계 API URI 생성
     */
    private URI buildPerformanceUri(CarPerformanceStatRequest request) {
        return UriComponentsBuilder.fromHttpUrl(PERFORMANCE_API_URL)
                .queryParam("serviceKey", tsCarApiKey)
                .queryParam("registYy", request.getRegistYy())
                .queryParam("registMt", request.getRegistMt())
                .queryParam("vhctyAsortCode", request.getVhctyAsortCode())
                .queryParam("registGrcCode", request.getRegistGrcCode())
                .queryParam("prye", request.getPrye())
                .build(true)
                .toUri();
    }

    /**
     * 자동차 검사정보 통계 API URI 생성
     */
    private URI buildInspectionUri(CarInspectionStatRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(INSPECTION_API_URL)
                .queryParam("serviceKey", tsCarApiKey)
                .queryParam("bgnde", request.getBgnde())
                .queryParam("endde", request.getEndde());

        if (request.getUseStrnghldLegaldongCode() != null && !request.getUseStrnghldLegaldongCode().isBlank()) {
            builder.queryParam("useStrnghldLegaldongCode", request.getUseStrnghldLegaldongCode());
        }
        if (request.getPrposSeNm() != null && !request.getPrposSeNm().isBlank()) {
            builder.queryParam("prposSeNm", request.getPrposSeNm());
        }
        if (request.getVhctyAsortNm() != null && !request.getVhctyAsortNm().isBlank()) {
            builder.queryParam("vhctyAsortNm", request.getVhctyAsortNm());
        }
        if (request.getVhctyClNm() != null && !request.getVhctyClNm().isBlank()) {
            builder.queryParam("vhctyClNm", request.getVhctyClNm());
        }

        return builder.build(true).toUri();
    }

    /**
     * XML 응답 공통 파싱 로직
     * 응답 구조: <response><header><resultCode>00</resultCode></header><body><dtaCo>건수</dtaCo></body></response>
     */
    private CarStatResponse parseXmlResponse(String xmlResponse, String verifiedAt) {
        if (xmlResponse == null || xmlResponse.isBlank()) {
            CarStatResponse response = CarStatResponse.unknown("EMPTY_RESPONSE", "빈 응답");
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            return response;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));
            document.getDocumentElement().normalize();

            String resultCode = getElementTextContent(document, "resultCode");

            if (!"00".equals(resultCode)) {
                String resultMsg = getElementTextContent(document, "resultMsg");
                String errorMessage = mapErrorCodeToMessage(resultCode, resultMsg);

                throw new VerifyException("API_ERROR_" + resultCode, errorMessage,
                        getHttpStatusForErrorCode(resultCode));
            }

            String dtaCoStr = getElementTextContent(document, "dtaCo");
            Integer dtaCo = null;
            if (dtaCoStr != null && !dtaCoStr.isBlank()) {
                try {
                    dtaCo = Integer.parseInt(dtaCoStr.trim());
                } catch (NumberFormatException e) {
                    log.warn("dtaCo 파싱 실패: {}", dtaCoStr);
                }
            }

            CarStatResponse response = CarStatResponse.success(dtaCo);
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            response.setRaw(xmlResponse);
            return response;

        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("XML 파싱 오류: {}", e.getMessage());
            CarStatResponse response = CarStatResponse.unknown("PARSE_ERROR", "XML 파싱 오류: " + e.getMessage());
            response.setProvider(PROVIDER_NAME);
            response.setVerifiedAt(verifiedAt);
            response.setRaw(xmlResponse);
            return response;
        }
    }

    /**
     * XML 요소에서 텍스트 내용 추출
     */
    private String getElementTextContent(Document document, String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Element element = (Element) nodeList.item(0);
            return element.getTextContent();
        }
        return null;
    }

    /**
     * 에러 코드를 메시지로 매핑
     */
    private String mapErrorCodeToMessage(String errorCode, String resultMsg) {
        if (resultMsg != null && !resultMsg.isBlank()) {
            return resultMsg;
        }

        return switch (errorCode) {
            case "01" -> "어플리케이션 에러";
            case "02" -> "데이터베이스 에러";
            case "03" -> "데이터 없음";
            case "04" -> "HTTP 에러";
            case "05" -> "서비스 연결 실패";
            case "10" -> "잘못된 요청 파라미터";
            case "11" -> "필수 요청 파라미터 누락";
            case "12" -> "해당 오픈 API 서비스가 없거나 폐기됨";
            case "20" -> "서비스 접근 거부";
            case "21" -> "일시적으로 사용할 수 없는 서비스키";
            case "22" -> "서비스 요청 제한 횟수 초과";
            case "30" -> "등록되지 않은 서비스키";
            case "31" -> "기한 만료된 서비스키";
            case "32" -> "등록되지 않은 IP";
            case "33" -> "서명되지 않은 호출";
            case "99" -> "기타 에러";
            default -> "알 수 없는 에러 (코드: " + errorCode + ")";
        };
    }

    /**
     * 에러 코드에 따른 HTTP 상태 코드 반환
     */
    private int getHttpStatusForErrorCode(String errorCode) {
        return switch (errorCode) {
            case "10", "11" -> 400;
            case "20", "21", "30", "31", "32", "33" -> 403;
            case "12" -> 404;
            case "22" -> 429;
            default -> 502;
        };
    }
}
