package com.skep.document;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
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

        Map<Long, String> names = new HashMap<>();
        for (Company c : companyRepo.findAllById(list.stream().map(DocumentReview::getSupplierCompanyId).distinct().toList())) {
            names.put(c.getId(), c.getName());
        }
        Map<Long, List<DocumentReviewItem>> itemsByReview = new HashMap<>();
        for (DocumentReviewItem it : itemRepo.findByReviewIdInOrderByIdAsc(list.stream().map(DocumentReview::getId).toList())) {
            itemsByReview.computeIfAbsent(it.getReviewId(), k -> new ArrayList<>()).add(it);
        }
        return list.stream()
                .map(r -> toMap(r, names.get(r.getSupplierCompanyId()), itemsByReview.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Transactional
    public void markRead(Long id, AuthenticatedUser actor) {
        load(id, actor).markRead();
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

    private Map<String, Object> toMap(DocumentReview r, String supplierName, List<DocumentReviewItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("supplier_company_id", r.getSupplierCompanyId());
        m.put("supplier_company_name", supplierName);
        m.put("message", r.getMessage());
        m.put("sent_at", r.getSentAt());
        m.put("read_at", r.getReadAt());
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
