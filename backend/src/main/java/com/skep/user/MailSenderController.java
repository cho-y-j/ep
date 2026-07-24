package com.skep.user;

import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.dto.MailSenderResponse;
import com.skep.user.dto.UpdateMailSenderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 발송 메일 계정 등록/조회. 비밀번호는 요청으로만 받고 응답에는 절대 포함하지 않는다. */
@RestController
@RequestMapping("/api/me/mail-sender")
@RequiredArgsConstructor
public class MailSenderController {

    private final UserMailSenderService service;

    @GetMapping
    public MailSenderResponse get(@CurrentUser AuthenticatedUser actor) {
        require(actor);
        return service.get(actor.id());
    }

    @PutMapping
    public MailSenderResponse update(@Valid @RequestBody UpdateMailSenderRequest req,
                                     @CurrentUser AuthenticatedUser actor) {
        require(actor);
        return service.update(actor.id(), req);
    }

    private void require(AuthenticatedUser actor) {
        if (actor == null) throw ApiException.unauthorized("NOT_AUTHENTICATED", "no auth principal");
    }
}
