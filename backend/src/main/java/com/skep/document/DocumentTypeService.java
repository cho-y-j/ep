package com.skep.document;

import com.skep.common.ApiException;
import com.skep.document.dto.CreateDocumentTypeRequest;
import com.skep.storage.FileStorage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
public class DocumentTypeService {

    private final DocumentTypeRepository repo;
    private final FileStorage storage;
    private final com.skep.collection.DocumentUploadMerger uploadMerger;

    public DocumentTypeService(DocumentTypeRepository repo, FileStorage storage,
                               com.skep.collection.DocumentUploadMerger uploadMerger) {
        this.repo = repo;
        this.storage = storage;
        this.uploadMerger = uploadMerger;
    }

    @Transactional(readOnly = true)
    public List<DocumentType> listForOwner(OwnerType appliesTo) {
        return repo.findByAppliesToAndActiveOrderBySortOrderAscIdAsc(appliesTo, true);
    }

    @Transactional(readOnly = true)
    public List<DocumentType> listAll() {
        return repo.findAllByOrderByAppliesToAscSortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public DocumentType get(Long id) {
        return repo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_TYPE_NOT_FOUND", "document type " + id + " not found"));
    }

    public DocumentType create(CreateDocumentTypeRequest req) {
        return repo.save(DocumentType.builder()
                .name(req.name())
                .appliesTo(req.appliesTo())
                .hasExpiry(req.hasExpiry())
                .requiresVerification(req.requiresVerification())
                .sortOrder(req.sortOrder())
                .active(true)
                .required(Boolean.TRUE.equals(req.required()))
                .blocksAssignment(Boolean.TRUE.equals(req.blocksAssignment()))
                .defaultValidMonths(req.defaultValidMonths())
                .ocrEnabled(Boolean.TRUE.equals(req.ocrEnabled()))
                .ocrExtractType(req.ocrExtractType())
                .ocrExpiryFieldKey(req.ocrExpiryFieldKey())
                .verifyEndpoint(req.verifyEndpoint())
                .requiredFields(req.requiredFields() != null ? req.requiredFields() : "[]")
                .build());
    }

    public DocumentType activate(Long id, boolean active) {
        DocumentType t = get(id);
        if (active) t.activate(); else t.deactivate();
        return t;
    }

    // ── V116: '샘플 보기' 예시 이미지 (마스킹은 ADMIN 이 수동, 시스템은 저장·표시만) ──

    /**
     * ADMIN: 서류종류에 마스킹된 예시 업로드(교체). 기존 파일이 있으면 삭제 후 교체.
     * 이미지 1장 → 이미지로, PDF 1개 → PDF로, 2개 이상 → 올린 순서대로 1개 PDF로 병합.
     */
    public DocumentType uploadSample(Long id, MultipartFile[] files) {
        DocumentType t = get(id);
        // 2개 이상이면 병합(PDF), 1개면 그대로. 어댑터가 빈 입력 시 400.
        MultipartFile file = uploadMerger.mergeToSingle(files);
        String ext = sampleExt(file.getContentType());
        if (ext == null) {
            throw ApiException.badRequest("UNSUPPORTED_SAMPLE_TYPE", "이미지(PNG/JPG/WEBP/GIF) 또는 PDF만 등록할 수 있습니다");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("FILE_READ_FAILED", "파일을 읽지 못했습니다");
        }
        String oldKey = t.getSampleImageKey();
        String key = storage.storeBytes(bytes, "." + ext);
        t.setSampleImageKey(key);
        repo.save(t);
        if (oldKey != null && !oldKey.isBlank()) storage.delete(oldKey);
        return t;
    }

    /** ADMIN: 샘플 이미지 삭제. */
    public DocumentType deleteSample(Long id) {
        DocumentType t = get(id);
        String key = t.getSampleImageKey();
        t.setSampleImageKey(null);
        repo.save(t);
        if (key != null && !key.isBlank()) storage.delete(key);
        return t;
    }

    /** 공개(무로그인): 샘플 이미지 로드 — 마스킹된 예시라 민감정보 아님. 없으면 404. */
    @Transactional(readOnly = true)
    public SampleImage loadSample(Long id) {
        DocumentType t = get(id);
        String key = t.getSampleImageKey();
        if (key == null || key.isBlank()) {
            throw ApiException.notFound("SAMPLE_NOT_FOUND", "등록된 샘플 이미지가 없습니다");
        }
        return new SampleImage(storage.load(key), contentTypeForKey(key));
    }

    /** 서류종류 응답에 넣을 샘플 URL. 미등록이면 null. */
    public static String sampleImageUrl(DocumentType t) {
        return t.getSampleImageKey() != null && !t.getSampleImageKey().isBlank()
                ? "/api/document-types/" + t.getId() + "/sample" : null;
    }

    /** 샘플이 PDF인지 — 저장 키 확장자로 판별(뷰어 img/iframe 분기용). 미등록이면 false. */
    public static boolean sampleIsPdf(DocumentType t) {
        String k = t.getSampleImageKey();
        return k != null && k.toLowerCase().endsWith(".pdf");
    }

    /** 허용 샘플 content-type → 저장 확장자. 이미지 + PDF 허용, 그 외는 null. */
    private static String sampleExt(String ct) {
        return switch (ct == null ? "" : ct.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "application/pdf" -> "pdf";
            default -> null;
        };
    }

    /** 저장 키 확장자 → 응답 content-type. */
    private static String contentTypeForKey(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".png")) return "image/png";
        if (k.endsWith(".webp")) return "image/webp";
        if (k.endsWith(".gif")) return "image/gif";
        if (k.endsWith(".pdf")) return "application/pdf";
        return "image/jpeg";
    }

    public record SampleImage(Resource resource, String contentType) {}
}
