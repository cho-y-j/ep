package com.skep.legalinspection;

import com.skep.common.ApiException;
import com.skep.legalinspection.dto.SafetyCheckTemplateDtos.Response;
import com.skep.legalinspection.dto.SafetyCheckTemplateDtos.SaveRequest;
import com.skep.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 점검 템플릿 CRUD — ADMIN 전용(시스템 관리). items 는 프론트가 정의한 항목 배열 패스스루.
 * 점검원 화면은 target 별 활성 템플릿 1건을 읽어 체크리스트 렌더.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SafetyCheckTemplateService {

    private final SafetyCheckTemplateRepository repo;

    @Transactional(readOnly = true)
    public List<Response> list() {
        return repo.findAllByOrderByIdDesc().stream().map(Response::from).toList();
    }

    @Transactional(readOnly = true)
    public Response get(Long id) {
        return Response.from(getTemplate(id));
    }

    public Response create(SaveRequest req, AuthenticatedUser actor) {
        SafetyCheckTemplate t = SafetyCheckTemplate.create(actor.id());
        apply(t, req);
        repo.save(t);
        return Response.from(t);
    }

    public Response update(Long id, SaveRequest req) {
        SafetyCheckTemplate t = getTemplate(id);
        apply(t, req);
        return Response.from(t);
    }

    public void delete(Long id) {
        repo.delete(getTemplate(id));
    }

    private void apply(SafetyCheckTemplate t, SaveRequest req) {
        t.setName(req.name().trim());
        t.setTarget(req.target() == null || req.target().isBlank() ? "EQUIPMENT" : req.target().trim());
        t.setItems(req.items() != null ? new ArrayList<>(req.items()) : new ArrayList<Map<String, Object>>());
        if (req.active() != null) t.setActive(req.active());
        t.touch();
    }

    private SafetyCheckTemplate getTemplate(Long id) {
        return repo.findById(id).orElseThrow(() ->
                ApiException.notFound("TEMPLATE_NOT_FOUND", "점검 템플릿을 찾을 수 없습니다"));
    }
}
