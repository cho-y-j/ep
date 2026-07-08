package com.skep.document;

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

    /** 자원을 골라 서류를 자원별 zip 으로 묶어 임의 이메일로 검토 발송. */
    @PostMapping("/review-mail")
    public DocumentReviewMailService.ReviewMailResult sendReviewMail(
            @RequestBody SendDocumentReviewMailRequest req,
            @CurrentUser AuthenticatedUser actor) {
        return service.send(req, actor);
    }
}
