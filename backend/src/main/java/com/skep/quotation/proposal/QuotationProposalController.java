package com.skep.quotation.proposal;

import com.skep.quotation.dispatch.quote.QuotationExcelService;
import com.skep.quotation.proposal.dto.CreateProposalRequest;
import com.skep.quotation.proposal.dto.ProposalResponse;
import com.skep.quotation.proposal.dto.UpdateProposalRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/quotations")
public class QuotationProposalController {

    private final QuotationProposalService service;
    private final QuotationExcelService excelService;

    public QuotationProposalController(QuotationProposalService service, QuotationExcelService excelService) {
        this.service = service;
        this.excelService = excelService;
    }

    /** 공급사 제안 작성 — 공개입찰 견적에 대해 자기 자원으로 제안. */
    @PostMapping("/{requestId}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalResponse submit(@PathVariable Long requestId,
                                    @Valid @RequestBody CreateProposalRequest req,
                                    @CurrentUser AuthenticatedUser actor) {
        return service.submit(requestId, req, actor);
    }

    /** 견적의 모든 제안 — BP/ADMIN은 전체, 공급사는 자기 제안만. */
    @GetMapping("/{requestId}/proposals")
    public List<ProposalResponse> listByRequest(@PathVariable Long requestId,
                                                  @CurrentUser AuthenticatedUser actor) {
        return service.listByRequest(requestId, actor);
    }

    /** 공급사 자기 제안 목록. */
    @GetMapping("/proposals/mine")
    public List<ProposalResponse> listMine(@CurrentUser AuthenticatedUser actor) {
        return service.listMine(actor);
    }

    /** 공급사 제안 수정. */
    @PatchMapping("/proposals/{proposalId}")
    public ProposalResponse update(@PathVariable Long proposalId,
                                    @Valid @RequestBody UpdateProposalRequest req,
                                    @CurrentUser AuthenticatedUser actor) {
        return service.update(proposalId, req, actor);
    }

    /** 공급사 제안 철회. */
    @PostMapping("/proposals/{proposalId}/withdraw")
    public ProposalResponse withdraw(@PathVariable Long proposalId,
                                      @CurrentUser AuthenticatedUser actor) {
        return service.withdraw(proposalId, actor);
    }

    /** BP 최종 선정. */
    @PostMapping("/proposals/{proposalId}/finalize")
    public ProposalResponse finalize(@PathVariable Long proposalId,
                                      @CurrentUser AuthenticatedUser actor) {
        return service.finalize(proposalId, actor);
    }

    /** BP 견적 close — 남은 제안 자동 거절. */
    @PostMapping("/{requestId}/close")
    public void closeRequest(@PathVariable Long requestId,
                              @CurrentUser AuthenticatedUser actor) {
        service.closeRequest(requestId, actor);
    }

    /** 응찰 견적서 PDF — BP/ADMIN/본인 공급사. */
    @GetMapping(value = "/proposals/{proposalId}/quote.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> proposalQuotePdf(@PathVariable Long proposalId,
                                                    @RequestParam(defaultValue = "inline") String disposition,
                                                    @CurrentUser AuthenticatedUser actor) {
        QuotationProposal p = service.getForView(proposalId, actor);
        byte[] bytes = excelService.buildProposalQuotePdf(p);
        return binary(bytes, "proposal-" + proposalId + ".pdf", MediaType.APPLICATION_PDF_VALUE, disposition);
    }

    /** 응찰 견적서 .xlsx. */
    @GetMapping(value = "/proposals/{proposalId}/quote.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> proposalQuoteXlsx(@PathVariable Long proposalId,
                                                     @RequestParam(defaultValue = "attachment") String disposition,
                                                     @CurrentUser AuthenticatedUser actor) {
        QuotationProposal p = service.getForView(proposalId, actor);
        byte[] bytes = excelService.buildProposalQuoteXlsx(p);
        return binary(bytes, "proposal-" + proposalId + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", disposition);
    }

    private ResponseEntity<byte[]> binary(byte[] bytes, String fname, String contentType, String disposition) {
        String encoded = URLEncoder.encode(fname, StandardCharsets.UTF_8).replace("+", "%20");
        String headerVal = "attachment".equalsIgnoreCase(disposition)
                ? "attachment; filename*=UTF-8''" + encoded
                : "inline; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, headerVal)
                .body(bytes);
    }
}
