package com.skep.docx;

import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * DOCX 템플릿 CRUD + 권한.
 *
 * - ADMIN: 전역(company_id NULL) + 회사 템플릿 모두 관리.
 * - BP / 공급사: 자기 회사 + 전역 템플릿 read 가능. 자기 회사 템플릿만 업로드/수정/삭제.
 */
@Service
@Transactional
public class DocxTemplateService {

    public static final String TARGET_WORK_PLAN = "WORK_PLAN";

    private final DocxTemplateRepository repo;
    private final FileStorage storage;

    public DocxTemplateService(DocxTemplateRepository repo, FileStorage storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<DocxTemplate> listVisible(String targetType, AuthenticatedUser actor) {
        if (actor == null || actor.role() == Role.ADMIN) {
            // actor null = 공개 컨텍스트 (사인 토큰). 전역 + 모든 회사 템플릿 노출.
            return repo.findByTargetTypeOrderByIdDesc(targetType);
        }
        return repo.findVisibleForCompany(targetType, actor.companyId());
    }

    /** 공개 컨텍스트(사인 토큰)에서 호출. 권한 체크 skip. */
    @Transactional(readOnly = true)
    public DocxTemplate getByIdPublic(Long id) {
        return getOrThrow(id);
    }

    public DocxTemplate upload(String targetType, Long companyId, String name,
                               MultipartFile file, AuthenticatedUser actor) {
        validateUpload(file);
        ensureCanWrite(actor, companyId);
        if (companyId != null && actor.role() != Role.ADMIN
                && !companyId.equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "다른 회사 템플릿을 업로드할 수 없습니다");
        }
        String key = storage.store(file);
        DocxTemplate t = repo.save(DocxTemplate.builder()
                .targetType(targetType)
                .companyId(companyId)
                .name(name)
                .fileKey(key)
                .fileSize(file.getSize())
                .uploadedBy(actor.id())
                .build());
        return t;
    }

    public DocxTemplate rename(Long id, String newName, AuthenticatedUser actor) {
        DocxTemplate t = getOrThrow(id);
        ensureCanWrite(actor, t.getCompanyId());
        t.rename(newName);
        return t;
    }

    public void delete(Long id, AuthenticatedUser actor) {
        DocxTemplate t = getOrThrow(id);
        ensureCanWrite(actor, t.getCompanyId());
        String key = t.getFileKey();
        repo.delete(t);
        if (key != null) storage.delete(key);
    }

    @Transactional(readOnly = true)
    public DocxTemplate getForExport(Long id, AuthenticatedUser actor) {
        DocxTemplate t = getOrThrow(id);
        // 전역(NULL) 또는 자기 회사 + ADMIN 은 전체.
        if (actor.role() == Role.ADMIN) return t;
        if (t.getCompanyId() == null) return t;
        if (t.getCompanyId().equals(actor.companyId())) return t;
        throw ApiException.forbidden("TEMPLATE_DENIED", "이 템플릿에 접근할 수 없습니다");
    }

    public Resource loadFile(DocxTemplate t) { return storage.load(t.getFileKey()); }

    private DocxTemplate getOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() ->
                ApiException.notFound("TEMPLATE_NOT_FOUND", "템플릿을 찾을 수 없습니다"));
    }

    private void ensureCanWrite(AuthenticatedUser actor, Long companyId) {
        if (actor.role() == Role.ADMIN) return;
        if (companyId == null) {
            throw ApiException.forbidden("GLOBAL_TEMPLATE_ADMIN_ONLY", "전역 템플릿은 ADMIN 만 관리할 수 있습니다");
        }
        if (actor.companyId() == null || !companyId.equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "이 템플릿을 수정할 권한이 없습니다");
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "파일이 비어 있습니다");
        }
        String ct = file.getContentType();
        String original = file.getOriginalFilename();
        boolean okCT = ct != null && (ct.contains("officedocument.wordprocessingml.document")
                || ct.equals("application/octet-stream"));
        boolean okExt = original != null && original.toLowerCase().endsWith(".docx");
        if (!okCT && !okExt) {
            throw ApiException.badRequest("INVALID_DOCX", "DOCX 파일만 업로드할 수 있습니다");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "DOCX 파일은 10MB 이하만 가능합니다");
        }
        // 실제 ZIP/DOCX 구조 검증 — magic + [Content_Types].xml + word/document.xml 존재
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B || bytes[2] != 0x03 || bytes[3] != 0x04) {
                throw ApiException.badRequest("INVALID_DOCX", "DOCX(ZIP) 형식이 아닙니다");
            }
            boolean hasCT = false, hasDoc = false;
            try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if ("[Content_Types].xml".equals(e.getName())) hasCT = true;
                    else if ("word/document.xml".equals(e.getName())) hasDoc = true;
                    if (hasCT && hasDoc) break;
                }
            }
            if (!hasCT || !hasDoc) {
                throw ApiException.badRequest("INVALID_DOCX",
                        "DOCX 내부 구조가 올바르지 않습니다 (Content_Types.xml / word/document.xml 누락)");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_DOCX", "DOCX 파일 검증 실패: " + e.getMessage());
        }
    }
}
