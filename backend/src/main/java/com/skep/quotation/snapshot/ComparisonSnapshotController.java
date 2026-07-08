package com.skep.quotation.snapshot;

import com.skep.common.ApiException;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.snapshot.dto.ComparisonSnapshotResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ComparisonSnapshotController {

    private final ComparisonSnapshotRepository repo;
    private final QuotationRequestRepository requests;

    /** BP 회사의 모든 비교 snapshot — 회사 상세 거래이력 탭에서 사용. ADMIN/BP 본인만. */
    @GetMapping("/api/companies/{companyId}/comparison-snapshots")
    public List<ComparisonSnapshotResponse> listByCompany(
            @PathVariable Long companyId,
            @CurrentUser AuthenticatedUser actor
    ) {
        if (actor.role() != Role.ADMIN && !companyId.equals(actor.companyId())) {
            throw ApiException.forbidden("NOT_PERMITTED", "조회 권한 없음");
        }
        return repo.findAllByBpCompanyId(companyId).stream()
                .map(ComparisonSnapshotResponse::from)
                .toList();
    }

    /** 견적별 snapshot 단건. ADMIN 전체 + 해당 BP (bpCompanyId or onBehalfOfBpCompanyId 일치) 만. */
    @GetMapping("/api/quotations/{requestId}/comparison-snapshot")
    public ComparisonSnapshotResponse getByRequest(
            @PathVariable Long requestId,
            @CurrentUser AuthenticatedUser actor
    ) {
        var snap = repo.findByQuotationRequestId(requestId)
                .orElseThrow(() -> ApiException.notFound("NO_SNAPSHOT", "비교 snapshot 없음"));
        if (actor.role() == Role.ADMIN) return ComparisonSnapshotResponse.from(snap);

        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        Long actorCo = actor.companyId();
        if (actorCo == null) throw ApiException.forbidden("NO_COMPANY", "회사 미지정");
        boolean isOwningBp = actorCo.equals(qr.getBpCompanyId())
                || actorCo.equals(qr.getOnBehalfOfBpCompanyId());
        if (!isOwningBp) {
            throw ApiException.forbidden("NOT_PERMITTED", "조회 권한 없음");
        }
        return ComparisonSnapshotResponse.from(snap);
    }
}
