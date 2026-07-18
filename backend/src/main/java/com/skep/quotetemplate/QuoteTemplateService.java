package com.skep.quotetemplate;

import com.skep.common.ApiException;
import com.skep.quotetemplate.dto.QuoteTemplateResponse;
import com.skep.quotetemplate.dto.SaveQuoteTemplateRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 견적 템플릿(단가표) CRUD — 공급사 자기 회사 전용(§3.3). ADMIN 은 전체.
 * rows 는 프론트가 정의한 라인 배열을 그대로 저장(패스스루). 발송 화면에서 불러와 삽입.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QuoteTemplateService {

    private final QuoteTemplateRepository repo;

    public QuoteTemplateResponse create(SaveQuoteTemplateRequest req, AuthenticatedUser actor) {
        Long supplierId = requireSupplier(actor);
        QuoteTemplate t = QuoteTemplate.create(supplierId, actor.id());
        apply(t, req);
        repo.save(t);
        return QuoteTemplateResponse.from(t);
    }

    public QuoteTemplateResponse update(Long id, SaveQuoteTemplateRequest req, AuthenticatedUser actor) {
        QuoteTemplate t = getForWrite(id, actor);
        apply(t, req);
        return QuoteTemplateResponse.from(t);
    }

    @Transactional(readOnly = true)
    public List<QuoteTemplateResponse> list(AuthenticatedUser actor) {
        List<QuoteTemplate> rows;
        if (actor.role() == Role.ADMIN) {
            rows = repo.findAll().stream()
                    .sorted(Comparator.comparingLong(QuoteTemplate::getId).reversed()).toList();
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            rows = actor.companyId() == null ? List.of()
                    : repo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId());
        } else {
            rows = List.of();
        }
        return rows.stream().map(QuoteTemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public QuoteTemplateResponse get(Long id, AuthenticatedUser actor) {
        return QuoteTemplateResponse.from(getForRead(id, actor));
    }

    public void delete(Long id, AuthenticatedUser actor) {
        QuoteTemplate t = getForWrite(id, actor);
        repo.delete(t);
    }

    // ── helpers ──────────────────────────────────────────────

    private Long requireSupplier(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 견적 템플릿을 등록할 수 있습니다");
        }
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        return actor.companyId();
    }

    private void apply(QuoteTemplate t, SaveQuoteTemplateRequest req) {
        t.setName(req.name().trim());
        t.setMemo(trimToNull(req.memo()));
        t.setRows(req.rows() != null ? new ArrayList<>(req.rows()) : new ArrayList<Map<String, Object>>());
        t.touch();
    }

    private QuoteTemplate getForWrite(Long id, AuthenticatedUser actor) {
        QuoteTemplate t = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("TEMPLATE_NOT_FOUND", "견적 템플릿을 찾을 수 없습니다"));
        if (actor.role() == Role.ADMIN) return t;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && t.getSupplierCompanyId().equals(actor.companyId())) return t;
        throw ApiException.forbidden("DENIED", "본인 회사 템플릿만 수정할 수 있습니다");
    }

    private QuoteTemplate getForRead(Long id, AuthenticatedUser actor) {
        QuoteTemplate t = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("TEMPLATE_NOT_FOUND", "견적 템플릿을 찾을 수 없습니다"));
        if (actor.role() == Role.ADMIN) return t;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && t.getSupplierCompanyId().equals(actor.companyId())) return t;
        throw ApiException.forbidden("DENIED", "조회 권한이 없습니다");
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }
}
