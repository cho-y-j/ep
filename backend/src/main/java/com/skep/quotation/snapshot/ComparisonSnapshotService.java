package com.skep.quotation.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.quotation.proposal.QuotationProposal;
import com.skep.quotation.proposal.QuotationProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 선정 시점 비교증거 동결. finalize 마다 호출되며 단일 snapshot per request 를 갱신.
 * 응찰 모든 proposals (WITHDRAWN 제외) 의 supplier/가격/노트/제출시점을 JSON 으로 직렬화.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComparisonSnapshotService {

    private final ComparisonSnapshotRepository repo;
    private final QuotationProposalRepository proposals;
    private final CompanyRepository companies;
    private final ObjectMapper jackson = new ObjectMapper();

    @Transactional
    public void captureForRequest(Long requestId, Long selectedProposalId, Long selectedBy, String reason) {
        List<QuotationProposal> all = proposals.findByRequestIdOrderByIdDesc(requestId);
        if (all.isEmpty()) return;

        var supplierIds = all.stream().map(QuotationProposal::getSupplierCompanyId).distinct().toList();
        Map<Long, String> names = companies.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));

        var entries = all.stream().map(p -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("proposalId", p.getId());
            e.put("supplierId", p.getSupplierCompanyId());
            e.put("supplierName", names.getOrDefault(p.getSupplierCompanyId(), "supplier#" + p.getSupplierCompanyId()));
            e.put("status", p.getStatus().name());
            e.put("dailyRate", p.getDailyRate());
            e.put("monthlyRate", p.getMonthlyRate());
            e.put("note", p.getNote());
            e.put("submittedAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            return e;
        }).toList();

        String json;
        try {
            Map<String, Object> root = new HashMap<>();
            root.put("entries", entries);
            json = jackson.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("snapshot JSON 직렬화 실패 reqId={}: {}", requestId, e.getMessage());
            return;
        }

        var existing = repo.findByQuotationRequestId(requestId);
        if (existing.isPresent()) {
            // 단일 snapshot per request — 갱신을 위해 delete + insert (selectedAt 새로 찍힘)
            repo.delete(existing.get());
            repo.flush();
        }
        repo.save(ComparisonSnapshot.builder()
                .quotationRequestId(requestId)
                .selectedProposalId(selectedProposalId)
                .selectedBy(selectedBy)
                .snapshotJson(json)
                .selectionReason(reason)
                .build());
    }
}
