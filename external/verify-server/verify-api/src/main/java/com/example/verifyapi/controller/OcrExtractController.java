package com.example.verifyapi.controller;

import com.example.verifyapi.dto.kosha.OCRData;
import com.example.verifyapi.exception.VerifyException;
import com.example.verifyapi.service.OCRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/verify/ocr")
public class OcrExtractController {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractController.class);
    private final OCRService ocrService;

    public OcrExtractController(OCRService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping(value = "/extract/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> extractByType(
            @PathVariable String type,
            @RequestParam("image") MultipartFile file) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        try {
            log.info("[{}] OCR 추출 요청: type={}, file={}, size={}",
                    requestId, type, file.getOriginalFilename(), file.getSize());

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "파일이 비어있습니다"));
            }

            BufferedImage image;
            try (InputStream is = file.getInputStream()) {
                String contentType = file.getContentType();
                if ("application/pdf".equalsIgnoreCase(contentType) || file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                    log.info("[{}] PDF 파일 감지, 이미지로 변환 중...", requestId);
                    image = convertPdfToImage(file, requestId);
                } else {
                    image = ImageIO.read(is);
                }
                if (image == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 이미지입니다"));
                }
            }

            OCRData ocrData = ocrService.extractText(image, requestId);
            String fullText = ocrData.getFullText() != null ? ocrData.getFullText() : "";

            log.info("[{}] OCR 전체 텍스트 길이: {}", requestId, fullText.length());

            Map<String, String> fields = parseByType(type.toUpperCase(), fullText, ocrData, requestId);

            // 주민번호 마스킹 처리 - textAnnotations의 실제 boundingPoly 사용
            String maskedImageBase64 = null;
            List<OCRData.TextAnnotation> annotations = ocrData.getTextAnnotations();

            if (annotations != null && !annotations.isEmpty()) {
                List<int[]> rrnRegions = findRRNBackRegionsFromAnnotations(fullText, annotations, requestId);
                if (!rrnRegions.isEmpty()) {
                    log.info("[{}] 주민번호 뒷자리 마스킹 영역 {}개 (boundingPoly 기반)", requestId, rrnRegions.size());
                    BufferedImage maskedImage = applyMasking(image, rrnRegions);
                    maskedImageBase64 = encodeImageToBase64(maskedImage);
                    log.info("[{}] 마스킹 완료, 이미지 크기: {}KB", requestId, maskedImageBase64.length() / 1024);
                }
            } else {
                // fallback: textAnnotations 없으면 추정 좌표 사용
                List<int[]> rrnRegions = findRRNBackRegionsEstimated(fullText, image.getWidth(), image.getHeight());
                if (!rrnRegions.isEmpty()) {
                    log.info("[{}] 주민번호 뒷자리 {}개 발견, 추정 좌표로 마스킹", requestId, rrnRegions.size());
                    BufferedImage maskedImage = applyMasking(image, rrnRegions);
                    maskedImageBase64 = encodeImageToBase64(maskedImage);
                    log.info("[{}] 마스킹 완료 (추정), 이미지 크기: {}KB", requestId, maskedImageBase64.length() / 1024);
                }
            }

            // 응답 — fields + (주민번호 검출 시) 마스킹된 이미지. fullText 는 미노출.
            // maskedImageBase64 는 skep 백엔드가 원본 대신 저장하는 데 사용 (PII 미보관).
            Map<String, Object> response = new HashMap<>(fields);
            if (maskedImageBase64 != null) {
                response.put("maskedImageBase64", maskedImageBase64);
            }

            log.info("[{}] OCR 추출 결과: type={}, fields={}, masked={}",
                    requestId, type, fields.keySet(), maskedImageBase64 != null);
            return ResponseEntity.ok(response);

        } catch (VerifyException e) {
            log.error("[{}] OCR 추출 실패: {}", requestId, e.getMessage());
            return ResponseEntity.status(e.getHttpStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[{}] OCR 추출 오류: {}", requestId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "OCR 추출 중 오류: " + e.getMessage()));
        } finally {
            MDC.remove("requestId");
        }
    }

    /**
     * textAnnotations의 boundingPoly를 사용하여 주민번호 뒷자리 영역 정확히 찾기
     *
     * 전략: fullText에서 주민번호 패턴(6자리-7자리)을 찾으면,
     * textAnnotations에서 해당 뒷7자리 숫자를 가진 요소의 좌표를 사용
     */
    private List<int[]> findRRNBackRegionsFromAnnotations(
            String fullText,
            List<OCRData.TextAnnotation> annotations,
            String requestId) {

        List<int[]> regions = new ArrayList<>();
        Pattern rrnPattern = Pattern.compile("(\\d{6})[\\-\\s](\\d{7})");
        Matcher matcher = rrnPattern.matcher(fullText);

        Set<String> rrnBackParts = new HashSet<>();
        while (matcher.find()) {
            String backPart = matcher.group(2);
            rrnBackParts.add(backPart);
            log.debug("[{}] 주민번호 뒷자리 발견: {}", requestId, backPart);
        }

        if (rrnBackParts.isEmpty()) {
            return regions;
        }

        // textAnnotations에서 주민번호 뒷자리와 매칭되는 요소 찾기
        for (OCRData.TextAnnotation ann : annotations) {
            String desc = ann.getDescription().replaceAll("[\\-\\s]", "");

            for (String backPart : rrnBackParts) {
                // 정확히 7자리 뒷부분이거나, 뒷부분을 포함하는 경우
                if (desc.equals(backPart) || desc.contains(backPart)) {
                    int x = ann.getMinX();
                    int y = ann.getMinY();
                    int width = ann.getMaxX() - x;
                    int height = ann.getMaxY() - y;

                    // 여유 마진 추가 (5px)
                    int margin = 5;
                    x = Math.max(0, x - margin);
                    y = Math.max(0, y - margin);
                    width += margin * 2;
                    height += margin * 2;

                    regions.add(new int[]{x, y, width, height});
                    log.info("[{}] 마스킹 영역 (boundingPoly): x={}, y={}, w={}, h={} for '{}'",
                            requestId, x, y, width, height, desc);
                    break;
                }
            }
        }

        // boundingPoly에서 못 찾으면, 주민번호 뒷자리 개별 숫자들의 좌표를 합치기
        if (regions.isEmpty() && !rrnBackParts.isEmpty()) {
            log.debug("[{}] 정확한 매칭 실패, 인접 숫자 기반 탐색 시작", requestId);
            regions.addAll(findRRNByAdjacentDigits(annotations, rrnBackParts, requestId));
        }

        return regions;
    }

    /**
     * 주민번호 뒷자리를 개별 숫자 annotation들의 위치로 찾기
     * Vision API가 "1234567"을 하나로 인식하지 않고 "1" "2" "3"... 개별로 분리할 수 있음
     */
    private List<int[]> findRRNByAdjacentDigits(
            List<OCRData.TextAnnotation> annotations,
            Set<String> rrnBackParts,
            String requestId) {

        List<int[]> regions = new ArrayList<>();

        // 주민번호 앞6자리 "-" 뒤7자리에서, "-" 또는 앞6자리의 좌표를 찾아서 그 오른쪽을 마스킹
        for (String backPart : rrnBackParts) {
            // "-" 기호 annotation 찾기
            for (int i = 0; i < annotations.size(); i++) {
                OCRData.TextAnnotation ann = annotations.get(i);
                String desc = ann.getDescription().trim();

                // "-" 또는 하이픈을 찾으면, 그 뒤에 오는 숫자들의 영역을 합산
                if ("-".equals(desc) || "\u2013".equals(desc) || "\u2014".equals(desc)) {
                    // "-" 이후 연속 숫자 annotations 모아서 영역 계산
                    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                    int maxX = 0, maxY = 0;
                    int digitCount = 0;

                    for (int j = i + 1; j < annotations.size() && digitCount < 7; j++) {
                        OCRData.TextAnnotation next = annotations.get(j);
                        if (next.getDescription().matches("\\d+")) {
                            minX = Math.min(minX, next.getMinX());
                            minY = Math.min(minY, next.getMinY());
                            maxX = Math.max(maxX, next.getMaxX());
                            maxY = Math.max(maxY, next.getMaxY());
                            digitCount += next.getDescription().length();
                        } else {
                            break;
                        }
                    }

                    if (digitCount >= 7) {
                        int margin = 5;
                        regions.add(new int[]{
                                Math.max(0, minX - margin),
                                Math.max(0, minY - margin),
                                (maxX - minX) + margin * 2,
                                (maxY - minY) + margin * 2
                        });
                        log.info("[{}] 마스킹 영역 (인접숫자): x={}, y={}, w={}, h={}",
                                requestId, minX, minY, maxX - minX, maxY - minY);
                        break;
                    }
                }
            }
        }

        return regions;
    }

    /**
     * Fallback: 추정 좌표 기반 마스킹 (textAnnotations 없을 때)
     */
    private List<int[]> findRRNBackRegionsEstimated(String fullText, int imageWidth, int imageHeight) {
        List<int[]> regions = new ArrayList<>();
        Pattern rrnPattern = Pattern.compile("(\\d{6})[\\-\\s](\\d{7})");
        Matcher matcher = rrnPattern.matcher(fullText);

        while (matcher.find()) {
            int estimatedX = (int)(imageWidth * 0.35);
            int estimatedY = (int)(imageHeight * 0.25);
            int estimatedWidth = (int)(imageWidth * 0.25);
            int estimatedHeight = (int)(imageHeight * 0.08);
            regions.add(new int[]{estimatedX, estimatedY, estimatedWidth, estimatedHeight});
        }

        return regions;
    }

    /**
     * 이미지에 마스킹 적용
     */
    private BufferedImage applyMasking(BufferedImage original, List<int[]> regions) {
        BufferedImage masked = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = masked.createGraphics();
        g2d.drawImage(original, 0, 0, null);

        g2d.setColor(Color.BLACK);
        for (int[] region : regions) {
            int x = region[0];
            int y = region[1];
            int width = region[2];
            int height = region[3];
            g2d.fillRect(x, y, width, height);
        }

        g2d.dispose();
        return masked;
    }

    /**
     * 이미지를 Base64로 인코딩
     */
    private String encodeImageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /** PDF를 이미지로 변환 */
    private BufferedImage convertPdfToImage(MultipartFile file, String requestId) throws IOException {
        java.io.File tempFile = java.io.File.createTempFile("ocr_pdf_", ".pdf");
        try {
            file.transferTo(tempFile);
            try (PDDocument document = PDDocument.load(tempFile)) {
                PDFRenderer renderer = new PDFRenderer(document);
                BufferedImage image = renderer.renderImageWithDPI(0, 200);
                log.info("[{}] PDF를 이미지로 변환: {}x{}", requestId, image.getWidth(), image.getHeight());
                return image;
            }
        } finally {
            tempFile.delete();
        }
    }

    private Map<String, String> parseByType(String type, String fullText, OCRData ocrData, String requestId) {
        switch (type) {
            case "LICENSE":
                return parseLicense(fullText, ocrData, requestId);
            case "BUSINESS":
                return parseBusiness(fullText, requestId);
            case "CARGO":
                return parseCargo(fullText, ocrData, requestId);
            case "EQUIPMENT_REGISTRATION":
                return parseVehicleRegistration(fullText, requestId);
            case "KOSHA":
                return parseKosha(ocrData, requestId);
            default:
                log.warn("[{}] 알 수 없는 타입: {}", requestId, type);
                return new HashMap<>();
        }
    }

    /** 운전면허증 파싱 */
    private Map<String, String> parseLicense(String text, OCRData ocrData, String requestId) {
        Map<String, String> fields = new HashMap<>();
        String[] lines = text.split("\\n");

        Pattern licensePattern = Pattern.compile("(\\d{2})[\\-\\s]?(\\d{2})[\\-\\s]?(\\d{6})[\\-\\s]?(\\d{2})");
        Matcher m = licensePattern.matcher(text);
        if (m.find()) {
            fields.put("licenseNumber", m.group(1) + "-" + m.group(2) + "-" + m.group(3) + "-" + m.group(4));
            log.debug("[{}] 면허번호 추출: {}", requestId, fields.get("licenseNumber"));
        }

        Pattern rrnPattern = Pattern.compile("(\\d{6})[\\-\\s](\\d{7})");
        Matcher rrnMatcher = rrnPattern.matcher(text);
        if (rrnMatcher.find()) {
            String yymmdd = rrnMatcher.group(1);
            String genderDigit = rrnMatcher.group(2).substring(0, 1);
            String century;
            if ("1".equals(genderDigit) || "2".equals(genderDigit) ||
                "5".equals(genderDigit) || "6".equals(genderDigit)) {
                century = "19";
            } else {
                century = "20";
            }
            String birth = century + yymmdd;
            fields.put("birth", birth);
            log.debug("[{}] 주민번호→생년월일 추출: {}", requestId, birth);
        }

        String name = null;
        if (name == null) {
            Pattern labelNamePattern = Pattern.compile("(?:이\\s*름|성\\s*명)[:\\s]*([가-힣]{2,5})");
            Matcher nm = labelNamePattern.matcher(text);
            if (nm.find()) {
                name = nm.group(1).trim();
            }
        }
        if (name == null) {
            for (int i = 0; i < lines.length - 1; i++) {
                if (licensePattern.matcher(lines[i].trim()).find()) {
                    String nextLine = lines[i + 1].trim();
                    if (nextLine.matches("^[가-힣]{2,5}$")) {
                        name = nextLine;
                        break;
                    }
                }
            }
        }
        if (name == null) {
            for (int i = 1; i < lines.length; i++) {
                if (rrnPattern.matcher(lines[i].trim()).find()) {
                    String prevLine = lines[i - 1].trim();
                    if (prevLine.matches("^[가-힣]{2,5}$")) {
                        name = prevLine;
                        break;
                    }
                }
            }
        }
        if (name == null) {
            Pattern excludePattern = Pattern.compile("면허|운전|License|보통|대형|소형|특수|종별|적성|갱신|주소|조건|발급|경찰|청장");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.matches("^[가-힣]{2,4}$") && !excludePattern.matcher(trimmed).find()) {
                    name = trimmed;
                    break;
                }
            }
        }
        if (name != null) {
            fields.put("name", name);
        }

        if (!fields.containsKey("name") && ocrData.getName() != null && !ocrData.getName().isEmpty()) {
            fields.put("name", ocrData.getName());
        }
        if (!fields.containsKey("birth") && ocrData.getBirthDate() != null && !ocrData.getBirthDate().isEmpty()) {
            fields.put("birth", ocrData.getBirthDate());
        }

        // 운전면허증 만료일/적성검사 만료 추출
        Pattern licenseExpiry = Pattern.compile("(?:적성검사기간|유효기간|갱신기간|갱신기한)[\\s\\S]{0,40}?(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");
        Matcher lem = licenseExpiry.matcher(text);
        if (lem.find()) {
            fields.put("expiryDate", lem.group(1) + "-"
                    + String.format("%02d", Integer.parseInt(lem.group(2))) + "-"
                    + String.format("%02d", Integer.parseInt(lem.group(3))));
            log.debug("[{}] 운전면허 만료일 추출: {}", requestId, fields.get("expiryDate"));
        }

        // 면허 종류 — 한 면허증에 복수 표기 가능 (예: 1종대형 + 1종보통). 정의 순서대로 전부 수집.
        List<Map.Entry<String, String>> typeCodes = List.of(
            Map.entry("1종대형", "11"), Map.entry("1종보통", "12"),
            Map.entry("1종소형", "13"), Map.entry("대형견인", "14"),
            Map.entry("구난차", "15"), Map.entry("소형견인", "16"),
            Map.entry("2종보통", "32"), Map.entry("2종소형", "33"),
            Map.entry("원동기", "38")
        );
        String compact = text.replaceAll("\\s+", "");
        List<String> foundTypes = new ArrayList<>();
        for (Map.Entry<String, String> tc : typeCodes) {
            if (compact.contains(tc.getKey())) {
                foundTypes.add(tc.getKey());
                // 진위확인용 종별 코드는 첫(상위) 종류 기준
                if (!fields.containsKey("licenseTypeCode")) {
                    fields.put("licenseTypeCode", tc.getValue());
                }
            }
        }
        if (!foundTypes.isEmpty()) {
            fields.put("licenseType", String.join(", ", foundTypes));
            log.debug("[{}] 면허종류 추출: {}", requestId, fields.get("licenseType"));
        }

        // 주소 — 시/도로 시작하는 라인 (인원 등록 시 주소 자동채움용, 선택값).
        Pattern addrLine = Pattern.compile(
            "((?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)"
            + "(?:특별시|광역시|특별자치시|특별자치도|도)?[^\\n]{2,60})");
        for (String line : lines) {
            Matcher am = addrLine.matcher(line.trim());
            if (am.find()) {
                String addr = am.group(1).trim().replaceAll("\\s{2,}", " ");
                fields.put("address", addr);
                log.debug("[{}] 주소 추출: {}", requestId, addr);
                break;
            }
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches(".*[A-Za-z].*") && trimmed.matches(".*\\d.*")
                && trimmed.length() >= 4
                && !trimmed.contains("License") && !trimmed.contains("Driver")
                && !licensePattern.matcher(trimmed).find()) {
                String cleaned = trimmed.replaceAll("[^A-Za-z0-9\\s\\-]", "").trim();
                if (cleaned.length() >= 4) {
                    fields.put("serialNo", cleaned);
                    break;
                }
            }
        }
        return fields;
    }

    /** 사업자등록증 파싱 */
    private Map<String, String> parseBusiness(String text, String requestId) {
        Map<String, String> fields = new HashMap<>();
        String[] lines = text.split("\\n");
        log.info("[{}] parseBusiness 시작, 텍스트 줄 수: {}", requestId, lines.length);

        Pattern bizLabelPattern = Pattern.compile("등록\\s*번호\\s*[:：]?\\s*(\\d{3})[\\-\\s]?(\\d{2})[\\-\\s]?(\\d{5})");
        Matcher blm = bizLabelPattern.matcher(text);
        if (blm.find()) {
            fields.put("businessNumber", blm.group(1) + blm.group(2) + blm.group(3));
        } else {
            Pattern bizPattern = Pattern.compile("(\\d{3})[\\-\\s]?(\\d{2})[\\-\\s]?(\\d{5})");
            Matcher bm = bizPattern.matcher(text);
            String firstMatch = null;
            while (bm.find()) {
                String candidate = bm.group(1) + bm.group(2) + bm.group(3);
                if (firstMatch == null) firstMatch = candidate;
                if (validateBizChecksum(candidate)) {
                    fields.put("businessNumber", candidate);
                    break;
                }
            }
            if (!fields.containsKey("businessNumber") && firstMatch != null) {
                fields.put("businessNumber", firstMatch);
            }
        }

        // S-9-G: OCR 이 한글 일부 누락하는 경우 (예: "성명"→"명", "상호"→"호", "개업연월일"→"업연월일") 도 매칭.
        // ":" / "：" 둘 다 지원, 라벨 단일 글자 fallback (한국어 OCR 결손 대응).
        Pattern repPattern = Pattern.compile("(?:대\\s*표\\s*자|성\\s*명|명)\\s*[:：]\\s*([가-힣]{2,5})");
        Matcher rm = repPattern.matcher(text);
        if (rm.find()) fields.put("representativeName", rm.group(1).trim());

        // (?<![가-힣]) — "호" 단독 라벨 매칭 시 "등록번호" 의 "호" 처럼 다른 한글 뒤에 붙은 경우 제외
        Pattern namePattern = Pattern.compile("(?<![가-힣])(?:상\\s*호|법인명|법\\s*인\\s*명|호)\\s*(?:\\(.*?\\))?\\s*[:：]\\s*([^\\n]{2,30})");
        Matcher nm = namePattern.matcher(text);
        if (nm.find()) {
            String bizName = nm.group(1).trim().replaceAll("^[\\s\\(\\)\\[\\]]+", "").replaceAll("[\\s\\(\\)\\[\\]]+$", "");
            if (!bizName.isEmpty()) fields.put("businessName", bizName);
        }

        Pattern startPattern = Pattern.compile("(?:개?업(?:연월)?일(?:자)?|개\\s*업\\s*일)\\s*[:：]?\\s*(\\d{4})[.년\\-\\s]*(\\d{1,2})[.월\\-\\s]*(\\d{1,2})");
        Matcher sm = startPattern.matcher(text);
        if (sm.find()) {
            fields.put("startDate", sm.group(1) + String.format("%02d", Integer.parseInt(sm.group(2))) + String.format("%02d", Integer.parseInt(sm.group(3))));
        }

        Pattern addrPattern = Pattern.compile("(?:사업장\\s*)?소\\s*재\\s*지\\s*[:：]?\\s*([^\\n]{5,100})");
        Matcher am = addrPattern.matcher(text);
        if (am.find()) {
            String address = am.group(1).trim().replaceAll("\\s+", " ");
            if (address.length() > 5) fields.put("address", address);
        }

        Pattern typePattern = Pattern.compile("(?:업\\s*태|업종)\\s*[:：]?\\s*([^\\n종]{1,30})");
        Matcher tm = typePattern.matcher(text);
        if (tm.find()) {
            String bizType = tm.group(1).trim();
            Pattern itemPattern = Pattern.compile("종\\s*목\\s*[:：]?\\s*([^\\n]{1,30})");
            Matcher im = itemPattern.matcher(text);
            if (im.find()) bizType = bizType + " / " + im.group(1).trim();
            if (!bizType.isEmpty()) fields.put("businessType", bizType.replaceAll("\\s+", " ").trim());
        }

        return fields;
    }

    /** 화물운송자격증 파싱 */
    private Map<String, String> parseCargo(String text, OCRData ocrData, String requestId) {
        Map<String, String> fields = new HashMap<>();
        String[] lines = text.split("\\n");
        log.info("[{}] parseCargo 시작, 텍스트 줄 수: {}", requestId, lines.length);

        Pattern lcnsLabelPattern = Pattern.compile("자격증\\s*번호\\s*[:：]?\\s*([\\d\\-]+)");
        Matcher lm = lcnsLabelPattern.matcher(text);
        if (lm.find()) {
            fields.put("lcnsNo", lm.group(1).trim());
        } else {
            Pattern lcnsNumPattern = Pattern.compile("(\\d{1,2})-(\\d{2})-(\\d{5,7})");
            Matcher lm2 = lcnsNumPattern.matcher(text);
            if (lm2.find()) fields.put("lcnsNo", lm2.group(0).trim());
        }

        Pattern namePatternLabel = Pattern.compile("(?:성\\s*명|이\\s*름)\\s*[:：]?\\s*([가-힣]{2,5})");
        Matcher nmm = namePatternLabel.matcher(text);
        if (nmm.find()) {
            fields.put("name", nmm.group(1).trim());
        } else if (ocrData.getName() != null && !ocrData.getName().isEmpty()) {
            fields.put("name", ocrData.getName());
        } else {
            Pattern excludePattern = Pattern.compile("자격|운송|화물|교통|안전|종사|면허|발급|취득|번호|확인|증명|국토");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.matches("^[가-힣]{2,4}$") && !excludePattern.matcher(trimmed).find()) {
                    fields.put("name", trimmed);
                    break;
                }
            }
        }

        Pattern birthLabelPattern = Pattern.compile("생년월일\\s*[:：]?\\s*(\\d{4})[.\\-/\\s]?(\\d{1,2})[.\\-/\\s]?(\\d{1,2})");
        Matcher bm = birthLabelPattern.matcher(text);
        if (bm.find()) {
            fields.put("birth", bm.group(1) + String.format("%02d", Integer.parseInt(bm.group(2))) + String.format("%02d", Integer.parseInt(bm.group(3))));
        } else if (ocrData.getBirthDate() != null && !ocrData.getBirthDate().isEmpty()) {
            fields.put("birth", ocrData.getBirthDate());
        } else {
            Pattern rrnPattern = Pattern.compile("(\\d{6})[\\-\\s](\\d{7})");
            Matcher rrm = rrnPattern.matcher(text);
            if (rrm.find()) {
                String yymmdd = rrm.group(1);
                String gd = rrm.group(2).substring(0, 1);
                String century = ("1".equals(gd) || "2".equals(gd) || "5".equals(gd) || "6".equals(gd)) ? "19" : "20";
                fields.put("birth", century + yymmdd);
            }
        }

        // 화물운송자격증 만료일 — "유효기간 2030.06.30"
        Pattern cargoExpiry = Pattern.compile("(?:유효기간|갱신기한|만료일)\\s*[:：]?\\s*(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");
        Matcher cem = cargoExpiry.matcher(text);
        if (cem.find()) {
            fields.put("expiryDate", cem.group(1) + "-"
                    + String.format("%02d", Integer.parseInt(cem.group(2))) + "-"
                    + String.format("%02d", Integer.parseInt(cem.group(3))));
            log.debug("[{}] 화물운송자격증 만료일 추출: {}", requestId, fields.get("expiryDate"));
        }

        return fields;
    }

    /** KOSHA 이수증 파싱 */
    private Map<String, String> parseKosha(OCRData ocrData, String requestId) {
        Map<String, String> fields = new HashMap<>();
        if (ocrData.getName() != null) fields.put("name", ocrData.getName());
        if (ocrData.getBirthDate() != null) fields.put("birthDate", ocrData.getBirthDate());
        if (ocrData.getRegistrationNumber() != null) fields.put("registrationNumber", ocrData.getRegistrationNumber());
        return fields;
    }

    /** 자동차등록증 파싱 */
    private Map<String, String> parseVehicleRegistration(String text, String requestId) {
        Map<String, String> fields = new HashMap<>();
        String[] lines = text.split("\\n");
        log.info("[{}] parseVehicleRegistration 시작, 텍스트 줄 수: {}", requestId, lines.length);

        Pattern regDatePattern1 = Pattern.compile("최\\s*초\\s*등\\s*록\\s*일\\s*[:：]?\\s*(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");
        Matcher rdm = regDatePattern1.matcher(text);
        if (rdm.find()) {
            fields.put("registrationDate", rdm.group(1) + "-" + String.format("%02d", Integer.parseInt(rdm.group(2))) + "-" + String.format("%02d", Integer.parseInt(rdm.group(3))));
            fields.put("productionYear", rdm.group(1));
        }

        int regNumLabelIdx = text.indexOf("자동차등록번호");
        if (regNumLabelIdx >= 0) {
            String afterLabel = text.substring(regNumLabelIdx, Math.min(regNumLabelIdx + 200, text.length()));
            Pattern vp1 = Pattern.compile("([가-힣]{1,2}\\d{2,3}[가-힣]\\s*\\d{4})");
            Matcher vm1 = vp1.matcher(afterLabel);
            if (vm1.find()) {
                fields.put("vehicleNumber", vm1.group(1).replaceAll("\\s+", ""));
            } else {
                Pattern vp2 = Pattern.compile("([가-힣]{1,2})(\\d{5,7})");
                Matcher vm2 = vp2.matcher(afterLabel);
                if (vm2.find()) fields.put("vehicleNumber", vm2.group(1) + vm2.group(2));
            }
        }
        if (!fields.containsKey("vehicleNumber")) {
            String searchText = text;
            int changeIdx = text.indexOf("번호변경");
            if (changeIdx > 0) searchText = text.substring(0, changeIdx);
            int oldNumIdx = searchText.indexOf("번호 :");
            if (oldNumIdx > 0) searchText = searchText.substring(0, oldNumIdx);
            Pattern vehicleNumPattern = Pattern.compile("([가-힣]{0,2}\\d{2,3}[가-힣]\\s*\\d{4})");
            Matcher vm = vehicleNumPattern.matcher(searchText);
            if (vm.find()) fields.put("vehicleNumber", vm.group(1).replaceAll("\\s+", ""));
        }

        Pattern modelPattern = Pattern.compile("차\\s*명\\s*[:：]?\\s*([^\\n]{2,50})");
        Matcher mm = modelPattern.matcher(text);
        if (mm.find()) {
            String modelName = mm.group(1).trim().replaceAll("\\s+", " ").replaceAll("^호\\s*", "");
            if (!modelName.isEmpty()) fields.put("modelName", modelName);
        }

        Pattern modelCodePattern = Pattern.compile("형\\s*식\\s*(?:및)?\\s*(?:제\\s*작\\s*연\\s*월)?\\s*[:：]?\\s*([A-Za-z0-9\\-]+)");
        Matcher mcm = modelCodePattern.matcher(text);
        if (mcm.find()) fields.put("modelCode", mcm.group(1).trim());

        if (!fields.containsKey("productionYear")) {
            Pattern prodYearPattern = Pattern.compile("제\\s*작\\s*연\\s*월\\s*[:：]?\\s*(\\d{4})[\\-년.\\s]\\s*(\\d{1,2})");
            Matcher pym = prodYearPattern.matcher(text);
            if (pym.find()) fields.put("productionYear", pym.group(1));
        }

        Pattern tonnagePattern = Pattern.compile("(?:적재(?:중량|량)|총\\s*중\\s*량|총중량)\\s*[:：]?\\s*([\\d,]+)\\s*(?:kg|KG|톤|t)?");
        Matcher tm = tonnagePattern.matcher(text);
        if (tm.find()) fields.put("tonnage", tm.group(1).replaceAll(",", ""));

        // 자동차등록증/보험증권 만료일 추출 — "검사유효기간"/"정기검사만료일"/"보험기간" 끝 날짜
        Pattern vehExpiry = Pattern.compile("(?:검사유효기간|정기검사\\s*만료일|보험기간|유효기간)[\\s\\S]{0,40}?(?:~|-|부터)?\\s*(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*(?:까지)?");
        Matcher vem = vehExpiry.matcher(text);
        // 보험기간 등은 두 날짜 (시작~끝). 마지막 매치(끝 날짜) 사용
        String lastExpiry = null;
        while (vem.find()) {
            lastExpiry = vem.group(1) + "-"
                    + String.format("%02d", Integer.parseInt(vem.group(2))) + "-"
                    + String.format("%02d", Integer.parseInt(vem.group(3)));
        }
        if (lastExpiry != null) {
            fields.put("expiryDate", lastExpiry);
            log.debug("[{}] 자동차/보험 만료일 추출: {}", requestId, lastExpiry);
        }

        return fields;
    }

    private boolean validateBizChecksum(String number) {
        if (number == null || number.length() != 10) return false;
        try {
            int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 5};
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += Character.getNumericValue(number.charAt(i)) * weights[i];
            }
            sum += (Character.getNumericValue(number.charAt(8)) * 5) / 10;
            int checkDigit = (10 - (sum % 10)) % 10;
            return checkDigit == Character.getNumericValue(number.charAt(9));
        } catch (Exception e) {
            return false;
        }
    }
}
