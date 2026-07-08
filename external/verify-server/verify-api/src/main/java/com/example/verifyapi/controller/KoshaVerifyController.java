package com.example.verifyapi.controller;

import com.example.verifyapi.dto.kosha.ExtractResult;
import com.example.verifyapi.dto.kosha.VerificationResult;
import com.example.verifyapi.exception.VerifyException;
import com.example.verifyapi.service.KoshaVerificationService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * KOSHA 교육이수증 검증 컨트롤러
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * 엔드포인트:
 * - POST /verify/kosha/upload : 이미지 업로드 및 검증
 * - POST /verify/kosha/extract : QR + OCR 추출만 (디버깅용)
 * - GET /verify/health : 헬스 체크
 */
@RestController
@RequestMapping("/verify")
public class KoshaVerifyController {

    private static final Logger log = LoggerFactory.getLogger(KoshaVerifyController.class);

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png"
    );
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final KoshaVerificationService verificationService;
    private final long maxFileSize;
    private final List<String> allowedTypes;
    private final int pdfDpi;

    public KoshaVerifyController(
            KoshaVerificationService verificationService,
            @Value("${verify.max-file-size:10485760}") long maxFileSize,
            @Value("${verify.allowed-types:jpg,jpeg,png,pdf}") String allowedTypesStr,
            @Value("${verify.pdf.dpi:200}") int pdfDpi) {
        this.verificationService = verificationService;
        this.maxFileSize = maxFileSize;
        this.allowedTypes = List.of(allowedTypesStr.toLowerCase().split(","));
        this.pdfDpi = pdfDpi;

        if (pdfDpi > 300) {
            log.warn("PDF DPI가 {}로 설정됨 - 높은 DPI는 메모리 사용량 증가를 유발할 수 있습니다", pdfDpi);
        }
        log.info("KoshaVerifyController 초기화: maxFileSize={}, allowedTypes={}, pdfDpi={}",
                maxFileSize, allowedTypes, pdfDpi);
    }

    /**
     * 교육이수증 이미지 업로드 및 검증
     */
    @PostMapping(value = "/kosha/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResult> uploadAndVerify(
            @RequestParam("image") MultipartFile file) {

        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        try {
            log.info("[{}] 검증 요청 수신: filename={}, size={}", requestId, file.getOriginalFilename(), file.getSize());

            // 파일 유효성 검증
            validateFile(file, requestId);

            // 이미지 로드
            BufferedImage image = loadImage(file, requestId);

            // 검증 수행
            VerificationResult result = verificationService.verify(image, requestId);

            return ResponseEntity.ok(result);

        } finally {
            MDC.remove("requestId");
        }
    }

    /**
     * QR + OCR 추출만 수행 (검증 없음, 디버깅용)
     */
    @PostMapping(value = "/kosha/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExtractResult> extractOnly(
            @RequestParam("image") MultipartFile file) {

        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        try {
            log.info("[{}] 추출 요청 수신: filename={}, size={}", requestId, file.getOriginalFilename(), file.getSize());

            // 파일 유효성 검증
            validateFile(file, requestId);

            // 이미지 로드
            BufferedImage image = loadImage(file, requestId);

            // 추출 수행
            ExtractResult result = verificationService.extract(image, requestId);

            return ResponseEntity.ok(result);

        } finally {
            MDC.remove("requestId");
        }
    }

    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    /**
     * 요청 ID 생성
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 파일 유효성 검증
     */
    private void validateFile(MultipartFile file, String requestId) {
        if (file == null || file.isEmpty()) {
            throw new VerifyException("FILE_EMPTY", "파일이 비어있습니다", 400);
        }

        if (file.getSize() > maxFileSize) {
            throw new VerifyException("FILE_TOO_LARGE",
                    String.format("파일 크기가 제한(%dMB)을 초과합니다", maxFileSize / 1024 / 1024), 400);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new VerifyException("FILE_INVALID", "파일 확장자를 확인할 수 없습니다", 400);
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!allowedTypes.contains(extension)) {
            throw new VerifyException("FILE_TYPE_NOT_ALLOWED",
                    String.format("허용되지 않는 파일 형식입니다. 허용: %s", allowedTypes), 400);
        }

        // Content-Type 검증
        String contentType = file.getContentType();
        if (contentType == null) {
            log.warn("[{}] Content-Type이 null입니다. 확장자 기반으로만 판단: extension={}", requestId, extension);
        } else {
            boolean isValidContentType = false;
            if ("pdf".equals(extension)) {
                isValidContentType = PDF_CONTENT_TYPE.equals(contentType);
            } else {
                isValidContentType = ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase());
            }

            if (!isValidContentType) {
                throw new VerifyException("FILE_CONTENT_TYPE_MISMATCH",
                        String.format("파일 Content-Type(%s)이 확장자(%s)와 일치하지 않습니다", contentType, extension), 400);
            }
        }

        log.debug("[{}] 파일 유효성 검증 통과: extension={}, contentType={}", requestId, extension, contentType);
    }

    /**
     * 이미지 로드 (PDF인 경우 첫 페이지를 이미지로 변환)
     */
    private BufferedImage loadImage(MultipartFile file, String requestId) {
        String originalFilename = file.getOriginalFilename();

        // 방어 코드: validateFile을 통과했더라도 안전하게 체크
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new VerifyException("FILE_INVALID", "파일 확장자를 확인할 수 없습니다", 400);
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

        try {
            if ("pdf".equals(extension)) {
                return loadPdfAsImage(file, requestId);
            } else {
                return loadImageFile(file, requestId);
            }
        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 이미지 로드 실패: {}", requestId, e.getMessage(), e);
            throw new VerifyException("IMAGE_LOAD_FAILED", "이미지 로드 실패: " + e.getMessage(), 400, e);
        }
    }

    /**
     * 일반 이미지 파일 로드
     */
    private BufferedImage loadImageFile(MultipartFile file, String requestId) throws IOException {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new VerifyException("IMAGE_INVALID", "유효하지 않은 이미지 파일입니다", 400);
            }
            log.debug("[{}] 이미지 로드 완료: {}x{}", requestId, image.getWidth(), image.getHeight());
            return image;
        }
    }

    /**
     * PDF 파일의 첫 페이지를 이미지로 변환
     */
    private BufferedImage loadPdfAsImage(MultipartFile file, String requestId) throws IOException {
        File tempPdfFile = null;
        try {
            // PDF를 임시 파일로 저장
            tempPdfFile = Files.createTempFile("verify_", ".pdf").toFile();
            file.transferTo(tempPdfFile);

            try (PDDocument document = PDDocument.load(tempPdfFile)) {
                if (document.getNumberOfPages() == 0) {
                    throw new VerifyException("PDF_EMPTY", "PDF 파일에 페이지가 없습니다", 400);
                }

                if (pdfDpi > 300) {
                    log.warn("[{}] PDF를 {}DPI로 렌더링 중 - 메모리 사용량이 높을 수 있습니다", requestId, pdfDpi);
                }

                PDFRenderer renderer = new PDFRenderer(document);
                BufferedImage image = renderer.renderImageWithDPI(0, pdfDpi);
                log.debug("[{}] PDF 첫 페이지 이미지 변환 완료: {}x{} ({}DPI)",
                        requestId, image.getWidth(), image.getHeight(), pdfDpi);
                return image;
            }
        } finally {
            deleteTempFile(tempPdfFile, requestId);
        }
    }

    /**
     * 임시 파일 삭제
     */
    private void deleteTempFile(File tempFile, String requestId) {
        if (tempFile != null && tempFile.exists()) {
            try {
                if (tempFile.delete()) {
                    log.debug("[{}] 임시 파일 삭제 완료: {}", requestId, tempFile.getName());
                } else {
                    log.warn("[{}] 임시 파일 삭제 실패: {}", requestId, tempFile.getName());
                }
            } catch (Exception e) {
                log.warn("[{}] 임시 파일 삭제 중 오류: {}", requestId, e.getMessage());
            }
        }
    }
}
