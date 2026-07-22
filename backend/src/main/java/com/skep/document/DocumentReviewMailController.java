package com.skep.document;

import com.skep.document.dto.ReviewBundlePdfRequest;
import com.skep.document.dto.SendDocumentReviewMailRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentReviewMailController {

    private final DocumentReviewMailService service;
    private final DocumentBundlePdfService bundleService;

    /** 자원을 골라 서류를 자원별 zip 으로 묶어 임의 이메일로 검토 발송. */
    @PostMapping("/review-mail")
    public DocumentReviewMailService.ReviewMailResult sendReviewMail(
            @RequestBody SendDocumentReviewMailRequest req,
            @CurrentUser AuthenticatedUser actor) {
        return service.send(req, actor);
    }

    /** 장비+교대조 조종원 서류를 장비별 병합 PDF로 묶어 이메일/BP사 발송. */
    @PostMapping("/review-bundle-pdf")
    public DocumentBundlePdfService.BundlePdfResult sendReviewBundlePdf(
            @RequestBody ReviewBundlePdfRequest req,
            @CurrentUser AuthenticatedUser actor) {
        return bundleService.send(req, actor);
    }
}
