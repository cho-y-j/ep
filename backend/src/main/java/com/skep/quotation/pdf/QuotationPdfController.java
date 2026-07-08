package com.skep.quotation.pdf;

import com.skep.quotation.dispatch.DispatchedEquipmentService;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/quotations/{requestId}/pdf")
@RequiredArgsConstructor
public class QuotationPdfController {

    private final QuotationPdfService pdfService;
    private final DispatchedEquipmentService dispatchedService;

    /**
     * mode=single → 한 공급사 dispatched 차량 견적서
     * mode=full   → BP 요청 전체 항목 포함 견적서
     * disposition=inline (기본) 또는 attachment.
     */
    @GetMapping
    public ResponseEntity<byte[]> render(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "single") String mode,
            @RequestParam(defaultValue = "inline") String disposition,
            @CurrentUser AuthenticatedUser actor
    ) {
        dispatchedService.ensureCanReadRequest(requestId, actor);

        QuotationPdfService.Mode m = "full".equalsIgnoreCase(mode)
                ? QuotationPdfService.Mode.FULL : QuotationPdfService.Mode.SINGLE;
        byte[] bytes = pdfService.render(requestId, m, actor);

        String fname = "quotation-" + requestId + (m == QuotationPdfService.Mode.FULL ? "-full" : "") + ".pdf";
        String encoded = URLEncoder.encode(fname, StandardCharsets.UTF_8).replace("+", "%20");
        String headerVal = "attachment".equalsIgnoreCase(disposition)
                ? "attachment; filename*=UTF-8''" + encoded
                : "inline; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, headerVal)
                .body(bytes);
    }
}
