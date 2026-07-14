package com.skep.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.VerificationStatus;
import com.skep.document.dto.DocumentResponse;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 서류 검증 + OCR 라우팅 서비스.
 *
 * 흐름:
 *   1. 자원 owner 권한 체크 (ADMIN / 자기 회사 공급사만 트리거 가능)
 *   2. document_types.ocr_enabled 면 verify-api OCR 추출 → extracted_data 저장
 *   3. document_types.verify_endpoint 가 있으면 main-api 정부 API 호출
 *      - KOSHA: 이미지 multipart
 *      - RIMS_LICENSE / CARGO_LICENSE / NTS_BIZ: extracted_data + 사용자 보충 필드
 *   4. 응답에서 verified 추출 → verification_status 업데이트
 *   5. verification_result 원본 JSON 저장
 *   6. audit log 기록
 */
@Service
@Transactional
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final VerifyClient client;
    private final NtsBizClient ntsClient;
    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companyRepo;
    private final FileStorage storage;
    private final AuditLogService auditLog;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VerificationService(VerifyClient client, NtsBizClient ntsClient,
                               DocumentRepository docRepo, DocumentTypeRepository typeRepo,
                               EquipmentRepository equipmentRepo, PersonRepository personRepo,
                               CompanyRepository companyRepo,
                               FileStorage storage, AuditLogService auditLog,
                               NotificationService notifications) {
        this.client = client;
        this.ntsClient = ntsClient;
        this.docRepo = docRepo;
        this.typeRepo = typeRepo;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.companyRepo = companyRepo;
        this.storage = storage;
        this.auditLog = auditLog;
        this.notifications = notifications;
    }

    /**
     * 수동 검증 트리거. 사용자가 보충한 필드(userInputs) + OCR 결과를 합쳐 verify-api 호출.
     *
     * @param userInputs 사용자 보충 필드 (license_no, name, license_condition_code 등). null 허용.
     */
    public DocumentResponse verifyDocument(Long documentId, Map<String, String> userInputs, AuthenticatedUser actor) {
        Document doc = docRepo.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("DOCUMENT_NOT_FOUND", "문서를 찾을 수 없습니다"));
        DocumentType type = typeRepo.findById(doc.getDocumentTypeId())
                .orElseThrow(() -> ApiException.notFound("DOCUMENT_TYPE_NOT_FOUND", "문서 타입을 찾을 수 없습니다"));

        Long ownerSupplierId = ownerSupplierIdOrThrow(doc);
        ensureCanVerify(actor, ownerSupplierId);

        if (type.getVerifyEndpoint() == null || type.getVerifyEndpoint().isBlank()) {
            throw ApiException.badRequest("VERIFY_NOT_SUPPORTED",
                    type.getName() + " 는 자동 검증 대상이 아닙니다");
        }

        // 1) 기존 doc.extractedData 에서 manual_* 필드 보존 (upload 시 사용자가 OCR preview 검토 후 입력한 값).
        Map<String, String> extractedFields = new HashMap<>();
        Map<String, String> manualFields = new HashMap<>();
        if (doc.getExtractedData() != null && !doc.getExtractedData().isBlank()) {
            try {
                JsonNode existing = objectMapper.readTree(doc.getExtractedData());
                Map<String, String> flat = flattenStringFields(existing);
                for (var e : flat.entrySet()) {
                    if (e.getKey() != null && e.getKey().startsWith("manual")) {
                        manualFields.put(e.getKey(), e.getValue());
                    }
                }
            } catch (Exception ignored) {
                /* 파싱 실패 시 manual 필드 무시 — OCR 으로 진행 */
            }
        }
        // 2) OCR 추출 — region template 보유 타입(운전면허/화물/안전교육)은 업로드 시 로컬 영역추출로
        //    manual_* 를 이미 채웠으므로(사용자 확정) Vision extractOcr 를 건너뛴다(중복·유료 호출 제거).
        //    템플릿 없는 타입(사업자등록증 NTS_BIZ 등)만 기존대로 Vision 사용 → Vision 경로 무손상.
        boolean hasRegionTemplate = type.getOcrRegionTemplate() != null && !type.getOcrRegionTemplate().isBlank();
        if (type.isOcrEnabled() && type.getOcrExtractType() != null && !hasRegionTemplate) {
            JsonNode ocr = runOcr(doc, type.getOcrExtractType());
            if (ocr != null) {
                extractedFields = flattenStringFields(ocr);
                // OCR + manual_* 둘 다 보존해서 audit 가능하도록 합본 저장.
                Map<String, Object> merged = new HashMap<>(extractedFields);
                merged.putAll(manualFields);
                doc.setExtractedData(safeJson(objectMapper.valueToTree(merged)));
            }
        }
        // 우선순위: userInputs (이번 호출 인자) > manual_* (upload 시 저장) > OCR
        extractedFields.putAll(manualFields);
        if (userInputs != null) extractedFields.putAll(userInputs);

        // 2) 정부 API 호출
        JsonNode result;
        switch (type.getVerifyEndpoint()) {
            case "RIMS_LICENSE" -> {
                // OCR 응답이 camelCase(licenseNumber) 로 저장되고, 사용자 user_inputs/manual_* 은 snake_case 일 수 있음.
                // 모든 가능한 표기를 fallback 으로 시도.
                String licenseNo = firstNonBlank(
                        extractedFields.get("manualLicenseNumber"),
                        extractedFields.get("manualLicenseNo"),
                        extractedFields.get("license_no"),
                        extractedFields.get("licenseNumber"),
                        extractedFields.get("licenseNo"));
                String name = firstNonBlank(
                        extractedFields.get("manualName"),
                        extractedFields.get("name"));
                String condCode = firstNonBlank(
                        extractedFields.get("manualLicenseConditionCode"),
                        extractedFields.get("manualLicenseType"),
                        extractedFields.get("manualLicenseTypeCode"),
                        extractedFields.get("license_condition_code"),
                        extractedFields.get("licenseType"),
                        extractedFields.get("licenseTypeCode"));
                // 면허종류 복수 선택("1종 대형, 1종 보통") 시 대표(첫 번째) 종류로 진위확인.
                if (condCode != null && condCode.contains(",")) {
                    condCode = condCode.substring(0, condCode.indexOf(',')).trim();
                }
                result = client.verifyDriverLicense(
                        licenseNo == null ? "" : licenseNo,
                        name == null ? "" : name,
                        condCode == null ? "" : condCode
                );
            }
            case "CARGO_LICENSE" -> {
                String cargoName = firstNonBlank(
                        extractedFields.get("manualName"), extractedFields.get("name"));
                String cargoBirth = firstNonBlank(
                        extractedFields.get("manualBirthDate"),
                        extractedFields.get("birth_date"),
                        extractedFields.get("birthDate"));
                String cargoNo = firstNonBlank(
                        extractedFields.get("manualLicenseNo"),
                        extractedFields.get("manualLicenseNumber"),
                        extractedFields.get("license_no"),
                        extractedFields.get("licenseNumber"),
                        extractedFields.get("licenseNo"));
                result = client.verifyCargo(
                        cargoName == null ? "" : cargoName,
                        cargoBirth == null ? "" : cargoBirth,
                        cargoNo == null ? "" : cargoNo
                );
            }
            case "NTS_BIZ" -> {
                // S-9-G: 사업자번호 우선순위
                //   1) manual_* (사용자 OCR preview 검토 후 입력) — 가장 신뢰
                //   2) verify-api OCR 결과 (businessNumber / biz_no 둘 다 인식)
                //   3) companies.business_number (가입 시 입력) — 마지막 fallback
                String bizNo = firstNonBlank(
                        extractedFields.get("manualBusinessNumber"),
                        extractedFields.get("manualBizNo"),
                        extractedFields.get("biz_no"),
                        extractedFields.get("businessNumber"));
                if ((bizNo == null || bizNo.isBlank()) && doc.getOwnerType() == com.skep.document.OwnerType.COMPANY) {
                    Company c = companyRepo.findById(doc.getOwnerId()).orElse(null);
                    if (c != null) bizNo = c.getBusinessNumber();
                }
                String startDate = firstNonBlank(
                        extractedFields.get("manualStartDate"),
                        extractedFields.get("start_date"),
                        extractedFields.get("startDate"));
                String ownerName = firstNonBlank(
                        extractedFields.get("manualRepresentativeName"),
                        extractedFields.get("manualOwnerName"),
                        extractedFields.get("owner_name"),
                        extractedFields.get("representativeName"));
                // 자체 NTS 클라이언트 우선 호출. NTS_SERVICE_KEY 미설정 시 verify-api 로 fallback.
                if (ntsClient.isEnabled()) {
                    result = ntsClient.lookupStatus(bizNo);
                } else {
                    result = client.verifyBusinessRegistration(bizNo, startDate, ownerName);
                }
                // S-9-G 정책: COMPANY 서류 — NTS 검증 통과한 경우에만 회사명 일치 검사.
                //   NTS pass + name match → VERIFIED 자동 통과
                //   NTS pass + name mismatch → OCR_REVIEW_REQUIRED + BIZNAME_MISMATCH (사용자 사유 ADMIN 에게)
                //   NTS fail → REJECTED (재업로드 필요, ADMIN 안 거침)
                boolean ntsVerified = result != null && result.has("verified") && result.get("verified").asBoolean();
                if (ntsVerified && doc.getOwnerType() == com.skep.document.OwnerType.COMPANY) {
                    Company c = companyRepo.findById(doc.getOwnerId()).orElse(null);
                    String dbName = c != null ? normalizeCompanyName(c.getName()) : "";
                    String certName = normalizeCompanyName(firstNonBlank(
                            extractedFields.get("manualBusinessName"),
                            extractedFields.get("businessName")));
                    if (!certName.isEmpty() && !dbName.isEmpty() && !certName.equals(dbName)) {
                        String userReason = firstNonBlank(
                                extractedFields.get("manualMismatchReason"),
                                "사용자 사유 미입력");
                        try {
                            com.fasterxml.jackson.databind.node.ObjectNode override = objectMapper.createObjectNode();
                            override.put("verified", false);
                            override.put("result", "REVIEW_REQUIRED");
                            override.put("reasonCode", "BIZNAME_MISMATCH");
                            override.put("message", "회사 등록명(" + (c != null ? c.getName() : "")
                                    + ") 과 cert 상호(" + firstNonBlank(
                                            extractedFields.get("manualBusinessName"),
                                            extractedFields.get("businessName"))
                                    + ") 불일치. 사유: " + userReason);
                            override.set("ntsRaw", result);
                            result = override;
                        } catch (Exception ignored) {
                            /* fallback to original result */
                        }
                    }
                }
            }
            case "KOSHA" -> {
                // 이미지 multipart 직접 검증.
                byte[] bytes = readFileBytes(doc);
                result = client.verifyKosha(bytes, doc.getFileName());
            }
            default -> throw ApiException.badRequest("UNKNOWN_VERIFY_ENDPOINT",
                    "알 수 없는 verify_endpoint: " + type.getVerifyEndpoint());
        }

        // 3) 결과 분기
        // NTS: { verified: true/false, ... } / KOSHA: { result: "VALID"/"INVALID"/"UNKNOWN", ... }
        boolean verified = result != null && (
                (result.has("verified") && result.get("verified").asBoolean())
                || (result.has("result") && "VALID".equalsIgnoreCase(result.get("result").asText()))
        );
        String reasonCode = result != null && result.has("reasonCode") ? result.get("reasonCode").asText() : null;
        // KOSHA result=UNKNOWN → ADMIN 검토 큐. result=INVALID → REJECTED.
        boolean koshaUnknown = result != null && result.has("result")
                && "UNKNOWN".equalsIgnoreCase(result.get("result").asText());

        doc.setVerificationResult(safeJson(result));

        // S-9-G 정책 (회사 사업자등록증 기준):
        //   1. NTS 검증 통과 + 회사명 일치 → VERIFIED 자동 통과
        //   2. NTS 검증 통과 + 회사명 불일치 → OCR_REVIEW_REQUIRED + BIZNAME_MISMATCH (사용자 사유 ADMIN 에게)
        //   3. NTS 검증 실패 (휴업/폐업/미등록) → REJECTED (사용자에게 재업로드 안내)
        //   4. 외부 API 실패 (UPSTREAM_ERROR/DISABLED) → OCR_REVIEW_REQUIRED (ADMIN 수동 처리)
        if (verified) {
            doc.markVerifiedBy(actor.id());
        } else if ("BIZNAME_MISMATCH".equals(reasonCode)) {
            // 정책 2: 사용자 사유 + ADMIN 결재 필요.
            doc.markOcrReviewRequired();
            doc.setRejectedReason(reasonCode);
        } else if ("UPSTREAM_ERROR".equals(reasonCode) || "UPSTREAM_DISABLED".equals(reasonCode)
                || "NTS_DISABLED".equals(reasonCode) || "BIZNO_MALFORMED".equals(reasonCode)
                || "INTERPRET_ERROR".equals(reasonCode) || koshaUnknown) {
            // 정책 4: 외부 API 일시 오류/비활성 또는 입력 불량(BIZNO_MALFORMED)·해석 실패 또는 KOSHA UNKNOWN
            //         — 사업자 상태 거부가 아니라 우리 측/데이터 품질 문제이므로 REJECTED 가 아닌 ADMIN 검토 큐로.
            doc.markOcrReviewRequired();
            doc.setRejectedReason(reasonCode);
        } else {
            // 정책 3: NTS 명시적 거부 (NTS_SUSPENDED/CLOSED/INVALID/NO_DATA) — REJECTED, 재업로드 안내.
            doc.markRejected(actor.id(), reasonCode != null ? reasonCode : "VERIFICATION_FAILED");
        }

        auditLog.record(actor, AuditAction.DOCUMENT_VERIFIED, AuditTargetType.DOCUMENT,
                doc.getId(), ownerSupplierId, null,
                "{\"status\":\"PENDING\"}",
                "{\"status\":\"" + doc.getVerificationStatus().name() + "\",\"verified\":" + verified + "}");

        // 알림 발신: 결과에 따라 owner 회사로 broadcast.
        notifyResult(doc, type, ownerSupplierId);
        return DocumentResponse.from(doc, type.getName(), type.isHasExpiry());
    }

    /** 검증 결과에 따라 알림 발신. 회사 broadcast (target_user_id null + target_company_id 자원 supplier). */
    private void notifyResult(Document doc, DocumentType type, Long ownerSupplierId) {
        if (ownerSupplierId == null) return;
        String typeName = type != null ? type.getName() : "서류";
        switch (doc.getVerificationStatus()) {
            case REJECTED -> notifications.sendToCompany(
                    ownerSupplierId, NotificationType.DOCUMENT_REJECTED,
                    typeName + " 반려됨",
                    typeName + " 가 반려되었습니다. 사유: "
                            + (doc.getRejectedReason() != null ? doc.getRejectedReason() : "확인 필요"),
                    "DOCUMENT", doc.getId(), null);
            case OCR_REVIEW_REQUIRED -> notifications.sendToCompany(
                    ownerSupplierId, NotificationType.DOCUMENT_OCR_REVIEW,
                    typeName + " OCR 검토 필요",
                    typeName + " 자동 검증이 어려워 OCR 검토 큐로 들어갔습니다. 보충 입력 후 재검증해 주세요.",
                    "DOCUMENT", doc.getId(), null);
            case VERIFIED -> notifications.sendToCompany(
                    ownerSupplierId, NotificationType.DOCUMENT_VERIFIED,
                    typeName + " 검증 완료",
                    typeName + " 가 정상적으로 검증되었습니다.",
                    "DOCUMENT", doc.getId(), null);
            default -> { /* PENDING — 알림 없음 */ }
        }
    }

    /**
     * 수동 반려. ADMIN 만 가능. 사유 필수.
     */
    public DocumentResponse rejectDocument(Long documentId, String reason, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("REJECT_ADMIN_ONLY", "반려는 관리자만 가능합니다");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "반려 사유는 필수입니다");
        }
        Document doc = docRepo.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("DOCUMENT_NOT_FOUND", "문서를 찾을 수 없습니다"));
        DocumentType type = typeRepo.findById(doc.getDocumentTypeId()).orElseThrow();
        Long ownerSupplierId = ownerSupplierIdOrThrow(doc);

        VerificationStatus before = doc.getVerificationStatus();
        doc.markRejected(actor.id(), reason);

        auditLog.record(actor, AuditAction.DOCUMENT_VERIFIED, AuditTargetType.DOCUMENT,
                doc.getId(), ownerSupplierId, null,
                "{\"status\":\"" + before.name() + "\"}",
                "{\"status\":\"REJECTED\",\"reason\":\"" + escape(reason) + "\"}");

        // 반려 알림.
        notifyResult(doc, type, ownerSupplierId);
        return DocumentResponse.from(doc, type.getName(), type.isHasExpiry());
    }

    /** 첫 번째 비어있지 않은 값 반환. */
    private static String firstNonBlank(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    /** 사업자번호 normalize (하이픈/공백 제거, 10자리 숫자만). */
    private static String normalize(String bizNo) {
        if (bizNo == null) return "";
        return bizNo.replaceAll("[^0-9]", "");
    }

    /**
     * 회사명 normalize — "(주)다인", "㈜다인", "주식회사 다인", "다인 (주)" 모두 "다인" 으로.
     * 공백/괄호/legal-entity prefix·suffix 제거 후 비교.
     */
    private static String normalizeCompanyName(String name) {
        if (name == null) return "";
        String s = name.trim();
        // 괄호 안 내용 제거 (예: "다인 (대표 홍길동)" → "다인 ")
        s = s.replaceAll("\\([^)]*\\)", "");
        // legal-entity 표기 제거
        s = s.replaceAll("㈜|\\(주\\)|주식회사|유한회사|\\(유\\)|합자회사|합명회사", "");
        // 공백/특수문자 제거
        s = s.replaceAll("[\\s·.,/_\\-]+", "");
        return s.toLowerCase();
    }

    /** 자원 owner 의 supplier_id 조회. */
    private Long ownerSupplierIdOrThrow(Document doc) {
        return switch (doc.getOwnerType()) {
            case EQUIPMENT -> equipmentRepo.findById(doc.getOwnerId())
                    .map(Equipment::getSupplierId)
                    .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 없음"));
            case PERSON -> personRepo.findById(doc.getOwnerId())
                    .map(Person::getSupplierId)
                    .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "인원 없음"));
            // S-9-G: 회사 서류 — owner 자체가 공급사 회사.
            case COMPANY -> doc.getOwnerId();
        };
    }

    /** ADMIN / 자기 회사 공급사만 검증 트리거. */
    private void ensureCanVerify(AuthenticatedUser actor, Long ownerSupplierId) {
        if (actor.role() == Role.ADMIN) return;
        // S-9-G: 모든 회사 사용자 (BP/EQUIPMENT_SUPPLIER/MANPOWER_SUPPLIER) 가 본인 회사 서류 검증 트리거 가능.
        if ((actor.role() == Role.BP || actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && ownerSupplierId.equals(actor.companyId())) return;
        throw ApiException.forbidden("VERIFY_DENIED", "검증 트리거 권한이 없습니다");
    }

    /** 파일을 byte[] 로 읽기 (KOSHA multipart 용). */
    private byte[] readFileBytes(Document doc) {
        try {
            Resource res = storage.load(doc.getFileKey());
            try (InputStream is = res.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("file read failed for doc {} key {}: {}", doc.getId(), doc.getFileKey(), e.getMessage());
            throw ApiException.badRequest("FILE_READ_ERROR", "파일을 읽지 못했습니다");
        }
    }

    /** verify-api OCR 호출 wrapper. */
    private JsonNode runOcr(Document doc, String ocrType) {
        try {
            byte[] bytes = readFileBytes(doc);
            return client.extractOcr(ocrType, bytes, doc.getFileName());
        } catch (Exception e) {
            log.warn("OCR extract failed doc={} type={}: {}", doc.getId(), ocrType, e.getMessage());
            return null;
        }
    }

    /** JSON 트리에서 String 형 leaf 만 평탄화하여 추출. 비-string (object/array) 는 무시. */
    private Map<String, String> flattenStringFields(JsonNode node) {
        Map<String, String> out = new HashMap<>();
        if (node == null || !node.isObject()) return out;
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v == null || v.isNull()) return;
            if (v.isTextual()) out.put(e.getKey(), v.asText());
            else if (v.isNumber() || v.isBoolean()) out.put(e.getKey(), v.asText());
        });
        return out;
    }

    private String safeJson(JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String s) {
        return com.skep.common.SafeText.escapeJson(s);
    }

    /** 단순 정적 파서: ObjectNode 만들어 반환. 외부 노출용. */
    public ObjectNode jsonObject() {
        return objectMapper.createObjectNode();
    }
}
