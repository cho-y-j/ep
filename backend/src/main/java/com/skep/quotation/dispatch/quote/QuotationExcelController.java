package com.skep.quotation.dispatch.quote;

import com.skep.common.ApiException;
import com.skep.quotation.dispatch.DispatchedEquipmentService;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 견적서 .xlsx / .pdf / 비교표 다운로드.
 * - GET .../quote.xlsx?supplier=N  : 공급사 N 의 한 시트 견적서
 * - GET .../quote.pdf?supplier=N   : 동일 데이터 PDF 미리보기
 * - GET .../compare.xlsx           : BP 전용 — 다중 공급사 횡 비교 .xlsx
 * - GET .../compare.pdf            : 동일 PDF (가로)
 */
@RestController
@RequestMapping("/api/quotations/{requestId}")
@RequiredArgsConstructor
public class QuotationExcelController {

    private final QuotationExcelService excelService;
    private final DispatchedEquipmentService dispatchedService;

    @GetMapping(value = "/quote.xlsx", produces = {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    public ResponseEntity<byte[]> downloadXlsx(
            @PathVariable Long requestId,
            @RequestParam(required = false) Long supplier,
            @RequestParam(defaultValue = "attachment") String disposition,
            @CurrentUser AuthenticatedUser actor
    ) {
        dispatchedService.ensureCanReadRequest(requestId, actor);
        Long sid = resolveSupplier(supplier, actor);
        byte[] bytes = excelService.buildSupplierQuote(requestId, sid);
        return binary(bytes,
                "quote-" + requestId + "-s" + sid + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                disposition);
    }

    @GetMapping(value = "/quote.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> previewPdf(
            @PathVariable Long requestId,
            @RequestParam(required = false) Long supplier,
            @RequestParam(defaultValue = "inline") String disposition,
            @CurrentUser AuthenticatedUser actor
    ) {
        dispatchedService.ensureCanReadRequest(requestId, actor);
        Long sid = resolveSupplier(supplier, actor);
        byte[] bytes = excelService.buildSupplierQuotePdf(requestId, sid);
        return binary(bytes,
                "quote-" + requestId + "-s" + sid + ".pdf",
                MediaType.APPLICATION_PDF_VALUE,
                disposition);
    }

    /** 발송 전 미리보기 — 단가 입력값으로 즉시 .xlsx 생성. DB write 없음.
     *  공급사라면 자기 회사 데이터 기준이므로 별도 가드 없이 role 만 확인.
     *  (TARGETED 선정 전 / OPEN_BID 응찰 전 단계도 호출 가능해야 함) */
    @PostMapping(value = "/quote-preview.xlsx", produces = {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    public ResponseEntity<byte[]> previewXlsx(
            @PathVariable Long requestId,
            @org.springframework.web.bind.annotation.RequestBody QuotationExcelService.PreviewRates rates,
            @CurrentUser AuthenticatedUser actor
    ) {
        ensureSupplierForPreview(actor);
        Long sid = resolveSupplier(null, actor);
        byte[] bytes = excelService.buildPreviewXlsx(requestId, sid, rates);
        return binary(bytes,
                "preview-" + requestId + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "inline");
    }

    @PostMapping(value = "/quote-preview.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> previewPdf(
            @PathVariable Long requestId,
            @org.springframework.web.bind.annotation.RequestBody QuotationExcelService.PreviewRates rates,
            @CurrentUser AuthenticatedUser actor
    ) {
        ensureSupplierForPreview(actor);
        Long sid = resolveSupplier(null, actor);
        byte[] bytes = excelService.buildPreviewPdf(requestId, sid, rates);
        return binary(bytes,
                "preview-" + requestId + ".pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "inline");
    }

    private void ensureSupplierForPreview(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw com.skep.common.ApiException.forbidden("SUPPLIER_ONLY",
                    "공급사만 미리보기 가능");
        }
    }

    @GetMapping(value = "/compare.xlsx", produces = {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    public ResponseEntity<byte[]> compareXlsx(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "attachment") String disposition,
            @CurrentUser AuthenticatedUser actor
    ) {
        ensureBpOrAdmin(actor);
        dispatchedService.ensureCanReadRequest(requestId, actor);
        byte[] bytes = excelService.buildComparison(requestId);
        return binary(bytes,
                "compare-" + requestId + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                disposition);
    }

    @GetMapping(value = "/compare.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> comparePdf(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "inline") String disposition,
            @CurrentUser AuthenticatedUser actor
    ) {
        ensureBpOrAdmin(actor);
        dispatchedService.ensureCanReadRequest(requestId, actor);
        byte[] bytes = excelService.buildComparisonPdf(requestId);
        return binary(bytes,
                "compare-" + requestId + ".pdf",
                MediaType.APPLICATION_PDF_VALUE,
                disposition);
    }

    /** 공급사 본인이면 자기 회사. BP/ADMIN 은 supplier param 필수. */
    private Long resolveSupplier(Long supplier, AuthenticatedUser actor) {
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            return actor.companyId();
        }
        if (supplier == null) {
            throw ApiException.badRequest("SUPPLIER_REQUIRED", "supplier 쿼리 파라미터가 필요합니다");
        }
        return supplier;
    }

    private void ensureBpOrAdmin(AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ONLY", "발주사/관리자만 비교표를 조회할 수 있습니다");
        }
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
