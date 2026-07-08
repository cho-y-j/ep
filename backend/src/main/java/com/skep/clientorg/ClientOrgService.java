package com.skep.clientorg;

import com.skep.clientorg.dto.ClientOrgResponse;
import com.skep.clientorg.dto.CreateClientOrgRequest;
import com.skep.clientorg.dto.UpdateClientOrgRequest;
import com.skep.clientorg.history.EquipmentClientOrgHistoryRepository;
import com.skep.clientorg.history.PersonClientOrgHistoryRepository;
import com.skep.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ClientOrgService {

    private final ClientOrgRepository repo;
    private final EquipmentClientOrgHistoryRepository eqHistRepo;
    private final PersonClientOrgHistoryRepository ppHistRepo;

    public ClientOrgService(ClientOrgRepository repo,
                             EquipmentClientOrgHistoryRepository eqHistRepo,
                             PersonClientOrgHistoryRepository ppHistRepo) {
        this.repo = repo;
        this.eqHistRepo = eqHistRepo;
        this.ppHistRepo = ppHistRepo;
    }

    @Transactional(readOnly = true)
    public List<ClientOrgResponse> listActive() {
        return repo.findByActiveOrderByNameAsc(true).stream().map(ClientOrgResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ClientOrgResponse> listAll() {
        return repo.findAll().stream()
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .map(ClientOrgResponse::from).toList();
    }

    public ClientOrgResponse create(CreateClientOrgRequest req) {
        if (repo.existsByCode(req.code())) {
            throw ApiException.badRequest("CLIENT_ORG_CODE_DUP", "이미 사용 중인 코드입니다: " + req.code());
        }
        ClientOrg c = repo.save(ClientOrg.builder()
                .name(req.name()).code(req.code()).note(req.note()).build());
        return ClientOrgResponse.from(c);
    }

    public ClientOrgResponse update(Long id, UpdateClientOrgRequest req) {
        ClientOrg c = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CLIENT_ORG_NOT_FOUND", "원청기관 " + id + " 없음"));
        if (req.code() != null && !req.code().equals(c.getCode()) && repo.existsByCode(req.code())) {
            throw ApiException.badRequest("CLIENT_ORG_CODE_DUP", "이미 사용 중인 코드입니다: " + req.code());
        }
        c.update(req.name(), req.code(), req.note());
        return ClientOrgResponse.from(c);
    }

    public void deactivate(Long id) {
        ClientOrg c = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CLIENT_ORG_NOT_FOUND", "원청기관 " + id + " 없음"));
        c.deactivate();
    }

    public void activate(Long id) {
        ClientOrg c = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CLIENT_ORG_NOT_FOUND", "원청기관 " + id + " 없음"));
        c.activate();
    }

    /** 완전 삭제 — 자원 이력에서 참조 중이면 거절 (FK RESTRICT). 이력 먼저 정리 후 시도. */
    public void hardDelete(Long id) {
        ClientOrg c = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CLIENT_ORG_NOT_FOUND", "원청기관 " + id + " 없음"));
        long eqCount = eqHistRepo.countByClientOrgId(id);
        long ppCount = ppHistRepo.countByClientOrgId(id);
        if (eqCount + ppCount > 0) {
            throw ApiException.badRequest("CLIENT_ORG_IN_USE",
                    "이 원청기관을 참조하는 자원 이력이 " + (eqCount + ppCount) + "건 있어 삭제할 수 없습니다. 비활성화를 사용하세요.");
        }
        repo.delete(c);
    }
}
