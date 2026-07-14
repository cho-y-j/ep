package com.skep.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.skep.verify.PaddleOcrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

/**
 * 업로드 시점 PII 이미지 마스킹 — 주민등록번호가 표기될 수 있는 서류(운전면허증 등)는
 * 로컬 paddle-ocr(/mask-pii) 로 주민번호 영역을 검정 처리한 이미지를 받아 원본 대신 저장한다.
 *
 * 서버 측에서 강제 적용하므로 OCR 다이얼로그를 거치지 않는 업로드 경로도 커버.
 * paddle 미가동 / 주민번호 미검출 / 오류 시 null 반환 → 원본 저장 (best-effort fail-open).
 * PDF 는 다중 페이지 유실 위험이 있어 제외 (이미지 파일만).
 */
@Component
public class PiiImageMasker {

    private static final Logger log = LoggerFactory.getLogger(PiiImageMasker.class);

    /**
     * 주민번호가 표기될 수 있는 서류 타입 이름 키워드.
     * 비교 전 타입 이름의 공백을 제거(normalize)하므로 "사업자 등록증"·"통장 사본" 도 매칭된다.
     * 실제 시드 타입명: 운전면허증/신분증/건강진단서/건강검진 결과서/자동차등록증/사업자 등록증/통장 사본/4대보험 가입증명원.
     */
    private static final List<String> PII_TYPE_KEYWORDS =
            List.of("면허", "신분증", "주민등록", "자동차등록", "건강", "사업자등록", "통장", "4대보험");

    private final PaddleOcrClient paddleClient;

    public PiiImageMasker(PaddleOcrClient paddleClient) {
        this.paddleClient = paddleClient;
    }

    public record MaskedFile(byte[] bytes, String contentType, String fileName) {}

    /**
     * PII 대상 서류 + 이미지 파일이면 주민번호 영역이 검정 처리된 마스킹본을 반환.
     * 비대상 / 주민번호 미검출 / 실패 시 null (호출부는 원본 저장).
     */
    public MaskedFile maskIfNeeded(String documentTypeName, MultipartFile file) {
        if (documentTypeName == null) return null;
        // 공백 제거 후 키워드 포함 검사 — "사업자 등록증"·"통장 사본" 등 공백 포함 타입명 매칭.
        String normalizedName = documentTypeName.replaceAll("\\s+", "");
        if (PII_TYPE_KEYWORDS.stream().noneMatch(normalizedName::contains)) return null;
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        // 이미지 외(PDF/Word 등)는 이 마스커로 처리 불가 — 원본 저장됨을 명시 경고.
        if (!ct.startsWith("image/")) {
            log.warn("PII mask 미적용(비이미지 — 원본 PII 저장 가능): type={} contentType={}", documentTypeName, ct);
            return null;
        }
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
            JsonNode res = paddleClient.maskPii(file.getBytes(), filename);
            // masked=true(주민번호 검출·검정 처리) + 이미지 base64 가 있을 때만 마스킹본 사용.
            if (res == null || !res.path("masked").asBoolean(false) || !res.hasNonNull("masked_image_base64")) {
                // 정책: best-effort fail-open. 마스킹 불가(주민번호 미검출/paddle 미가동) 시 원본 저장됨을 경고로 남긴다.
                log.warn("PII mask 미적용(주민번호 미검출/paddle 미가동 — 원본 PII 저장 가능): type={} file={}", documentTypeName, filename);
                return null;
            }
            byte[] bytes = Base64.getDecoder().decode(res.get("masked_image_base64").asText());
            String base = filename;
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            log.info("PII mask applied: type={} file={} maskedSize={}KB", documentTypeName, filename, bytes.length / 1024);
            return new MaskedFile(bytes, "image/png", base + "-masked.png");
        } catch (Exception e) {
            log.warn("PII mask failed — 원본 저장(원본 PII 저장 가능): type={} err={}", documentTypeName, e.getMessage());
            return null;
        }
    }
}
