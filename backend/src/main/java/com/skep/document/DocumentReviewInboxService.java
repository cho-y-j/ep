package com.skep.document;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.notification.NotificationService;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/** BP사 계정 수신함 — 받은 서류 심사 조회 / 읽음 처리 / 자원별 폴더로 묶은 zip 다운로드. */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentReviewInboxService {

    private final DocumentReviewRepository reviewRepo;
    private final DocumentReviewItemRepository itemRepo;
    private final DocumentZipService zipService;
    private final CompanyRepository companyRepo;
    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;
    private final NotificationService notifications;
    private final AuditLogService auditLog;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listReceived(AuthenticatedUser actor) {
        List<DocumentReview> list;
        if (actor.role() == Role.ADMIN) {
            list = reviewRepo.findAllByOrderByIdDesc();
        } else if (actor.role() == Role.BP && actor.companyId() != null) {
            list = reviewRepo.findByBpCompanyIdOrderByIdDesc(actor.companyId());
        } else {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 조회할 수 있습니다");
        }
        if (list.isEmpty()) return List.of();

        Map<Long, Company> companies = new HashMap<>();
        for (Company c : companyRepo.findAllById(list.stream().map(DocumentReview::getSupplierCompanyId).distinct().toList())) {
            companies.put(c.getId(), c);
        }
        Map<Long, List<DocumentReviewItem>> itemsByReview = new HashMap<>();
        for (DocumentReviewItem it : itemRepo.findByReviewIdInOrderByIdAsc(list.stream().map(DocumentReview::getId).toList())) {
            itemsByReview.computeIfAbsent(it.getReviewId(), k -> new ArrayList<>()).add(it);
        }
        return list.stream()
                .map(r -> toMap(r, companies.get(r.getSupplierCompanyId()), itemsByReview.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Transactional
    public void markRead(Long id, AuthenticatedUser actor) {
        load(id, actor).markRead();
    }

    /** 봉투 상세 — 자원(item)별 문서 목록. 체인 헤드 기준. 수신 BP 본인(또는 ADMIN)만. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDocuments(Long id, AuthenticatedUser actor) {
        load(id, actor);
        Map<Long, DocumentType> typeCache = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (DocumentReviewItem item : itemRepo.findByReviewIdOrderByIdAsc(id)) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("owner_type", item.getOwnerType().name());
            im.put("owner_id", item.getOwnerId());
            im.put("label", item.getLabel());
            List<Map<String, Object>> docList = new ArrayList<>();
            for (Document d : docRepo.findActiveHeadByOwner(item.getOwnerType(), item.getOwnerId())) {
                DocumentType t = typeCache.computeIfAbsent(d.getDocumentTypeId(),
                        k -> typeRepo.findById(k).orElse(null));
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("id", d.getId());
                dm.put("document_type_name", t != null ? t.getName() : "(삭제됨)");
                dm.put("file_name", d.getFileName());
                dm.put("expiry_date", d.getExpiryDate());
                dm.put("has_expiry", t != null && t.isHasExpiry());
                dm.put("verification_status", d.getVerificationStatus().name());
                dm.put("verified", d.isVerified());
                dm.put("rejected_reason", d.getRejectedReason());
                docList.add(dm);
            }
            im.put("documents", docList);
            out.add(im);
        }
        return out;
    }

    /** 수신 BP 가 봉투를 승인. PENDING 에서만 전이. 공급사에 인앱 알림. */
    @Transactional
    public Map<String, Object> approve(Long id, AuthenticatedUser actor) {
        DocumentReview r = loadForAction(id, actor);
        r.approve(actor.id());
        auditLog.record(actor, AuditAction.DOCUMENT_REVIEW_APPROVED, AuditTargetType.DOCUMENT_REVIEW,
                r.getId(), r.getSupplierCompanyId(), null, null, null);
        notifications.sendToCompany(r.getSupplierCompanyId(),
                "DOCUMENT_REVIEW_RESULT", "서류 심사 승인됨",
                "보내신 서류 심사가 승인되었습니다.",
                "DOCUMENT_REVIEW", r.getId(), null);
        return toMapOne(r);
    }

    /** 수신 BP 가 봉투를 반려. PENDING 에서만 전이. 사유 필수. 공급사에 인앱 알림. */
    @Transactional
    public Map<String, Object> reject(Long id, String reason, AuthenticatedUser actor) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "반려 사유를 입력하세요");
        }
        DocumentReview r = loadForAction(id, actor);
        String trimmed = reason.trim();
        r.reject(actor.id(), trimmed);
        auditLog.record(actor, AuditAction.DOCUMENT_REVIEW_REJECTED, AuditTargetType.DOCUMENT_REVIEW,
                r.getId(), r.getSupplierCompanyId(), null, null,
                "{\"reason\":\"" + trimmed.replace("\"", "\\\"") + "\"}");
        notifications.sendToCompany(r.getSupplierCompanyId(),
                "DOCUMENT_REVIEW_RESULT", "서류 심사 반려됨",
                "반려 사유: " + trimmed,
                "DOCUMENT_REVIEW", r.getId(), null);
        return toMapOne(r);
    }

    private DocumentReview loadForAction(Long id, AuthenticatedUser actor) {
        DocumentReview r = load(id, actor);
        if (r.getStatus() != DocumentReviewStatus.PENDING) {
            throw ApiException.badRequest("INVALID_STATE", "심사중 상태에서만 처리할 수 있습니다");
        }
        return r;
    }

    private Map<String, Object> toMapOne(DocumentReview r) {
        Company supplier = companyRepo.findById(r.getSupplierCompanyId()).orElse(null);
        return toMap(r, supplier, itemRepo.findByReviewIdOrderByIdAsc(r.getId()));
    }

    @Transactional(readOnly = true)
    public byte[] download(Long id, AuthenticatedUser actor) {
        load(id, actor);
        List<DocumentReviewItem> items = itemRepo.findByReviewIdOrderByIdAsc(id);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (DocumentReviewItem item : items) {
                String prefix = itemFolder(item);
                zipService.writeEntries(zos, item.getOwnerType(), item.getOwnerId(), prefix);
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("document review zip build fail {}: {}", id, e.getMessage());
            throw ApiException.badRequest("ZIP_FAIL", "서류 압축에 실패했습니다");
        }
    }

    /** 여러 봉투를 한 번에 — 봉투별(공급사_id) 폴더 아래 자원별 폴더로 묶은 1개 zip. 각 봉투는 읽음 처리. */
    @Transactional
    public byte[] downloadBulk(List<Long> ids, AuthenticatedUser actor) {
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("NO_SELECTION", "다운로드할 서류 심사를 선택하세요");
        }
        List<DocumentReview> reviews = ids.stream().distinct().map(id -> load(id, actor)).toList();
        Map<Long, String> names = new HashMap<>();
        for (Company c : companyRepo.findAllById(reviews.stream().map(DocumentReview::getSupplierCompanyId).distinct().toList())) {
            names.put(c.getId(), c.getName());
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (DocumentReview r : reviews) {
                String supplier = names.getOrDefault(r.getSupplierCompanyId(), "공급사" + r.getSupplierCompanyId());
                String envelope = com.skep.common.SafeText.sanitizeFileName(supplier + "_" + r.getId()) + "/";
                for (DocumentReviewItem item : itemRepo.findByReviewIdOrderByIdAsc(r.getId())) {
                    String prefix = envelope + itemFolder(item);
                    zipService.writeEntries(zos, item.getOwnerType(), item.getOwnerId(), prefix);
                }
                r.markRead();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("document review bulk zip build fail {}: {}", ids, e.getMessage());
            throw ApiException.badRequest("ZIP_FAIL", "서류 압축에 실패했습니다");
        }
    }

    /** 자원별 zip 폴더명 — 동일 label(동명이인 등) 충돌로 항목이 누락되지 않게 ownerId 를 부기한다. */
    private String itemFolder(DocumentReviewItem item) {
        return com.skep.common.SafeText.sanitizeFileName(item.getLabel() + "_" + item.getOwnerId()) + "/";
    }

    private DocumentReview load(Long id, AuthenticatedUser actor) {
        DocumentReview r = reviewRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("REVIEW_NOT_FOUND", "서류 심사를 찾을 수 없습니다"));
        boolean ok = actor.role() == Role.ADMIN
                || (actor.role() == Role.BP && actor.companyId() != null
                    && actor.companyId().equals(r.getBpCompanyId()));
        if (!ok) throw ApiException.forbidden("REVIEW_DENIED", "접근 권한이 없습니다");
        return r;
    }

    private Map<String, Object> toMap(DocumentReview r, Company supplier, List<DocumentReviewItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("supplier_company_id", r.getSupplierCompanyId());
        m.put("supplier_company_name", supplier == null ? null : supplier.getName());
        m.put("supplier_company_type", supplier == null ? null : supplier.getType().name());
        m.put("message", r.getMessage());
        m.put("sent_at", r.getSentAt());
        m.put("read_at", r.getReadAt());
        m.put("status", r.getStatus().name());
        m.put("rejected_reason", r.getRejectedReason());
        m.put("acted_by", r.getActedBy());
        m.put("acted_at", r.getActedAt());
        m.put("total_docs", items.stream().mapToInt(DocumentReviewItem::getDocCount).sum());
        m.put("items", items.stream().map(i -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("owner_type", i.getOwnerType().name());
            im.put("owner_id", i.getOwnerId());
            im.put("label", i.getLabel());
            im.put("doc_count", i.getDocCount());
            return im;
        }).toList());
        return m;
    }
}
