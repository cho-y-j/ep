package com.skep.settlement.statement;

import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.settlement.SettlementService;
import com.skep.settlement.dto.SettlementDtos.SettlementSummaryResponse;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 거래내역서(월별/날짜지정) 다운로드. SettlementService.summary 를 그대로 호출해(권한·소유자 스코프 재사용)
 * 얻은 숫자를 PDF/XLSX 로 렌더만 한다. 금액 재계산·프로레이션 없음.
 */
@RestController
@RequiredArgsConstructor
public class SettlementStatementController {

    private final SettlementService service;
    private final SettlementStatementPdfRenderer pdfRenderer;
    private final SettlementStatementXlsxRenderer xlsxRenderer;
    private final CompanyRepository companies;

    @GetMapping("/api/settlements/statement")
    public ResponseEntity<byte[]> statement(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "pdf") String format,
            @CurrentUser AuthenticatedUser actor
    ) {
        // summary 가 권한/소유자 스코프를 검증·확정한다(ADMIN=companyId 필수, 공급사=본인+협력사, BP=403).
        SettlementSummaryResponse data = service.summary(actor, companyId, from, to);

        Long selfId = actor.role() == Role.ADMIN ? companyId : actor.companyId();
        String companyName = companies.findById(selfId).map(Company::getName).orElse("#" + selfId);

        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] bytes = xlsx
                ? xlsxRenderer.render(companyName, from, to, data)
                : pdfRenderer.render(companyName, from, to, data);

        String contentType = xlsx
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : MediaType.APPLICATION_PDF_VALUE;
        String fname = "거래내역서_" + companyName + "_" + periodTag(from, to) + "." + (xlsx ? "xlsx" : "pdf");
        String encoded = URLEncoder.encode(fname, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(bytes);
    }

    private static String periodTag(LocalDate from, LocalDate to) {
        String f = from != null ? from.toString() : "";
        String t = to != null ? to.toString() : "";
        return (f.isEmpty() && t.isEmpty()) ? "전체" : f + "~" + t;
    }
}
