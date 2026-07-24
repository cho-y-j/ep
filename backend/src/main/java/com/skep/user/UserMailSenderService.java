package com.skep.user;

import com.skep.common.ApiException;
import com.skep.common.MailCredentialCipher;
import com.skep.common.SafeText;
import com.skep.user.dto.MailSenderResponse;
import com.skep.user.dto.UpdateMailSenderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 본인 발송 메일 계정(심사 메일 From) 등록/조회/해제. 비밀번호는 암호화 저장하며 응답에 노출하지 않는다. */
@Service
@RequiredArgsConstructor
public class UserMailSenderService {

    private final UserRepository users;
    private final MailCredentialCipher cipher;

    @Transactional(readOnly = true)
    public MailSenderResponse get(Long userId) {
        return MailSenderResponse.from(load(userId));
    }

    @Transactional
    public MailSenderResponse update(Long userId, UpdateMailSenderRequest req) {
        User u = load(userId);
        String email = req.email() == null ? null : req.email().trim();
        if (email == null || email.isBlank()) {
            u.clearMailSender(); // 해제 → 기본 계정 발송
            return MailSenderResponse.from(u);
        }
        if (!SafeText.isSafeEmail(email)) {
            throw ApiException.badRequest("BAD_EMAIL", "올바른 이메일 형식이 아닙니다");
        }
        String password = req.password();
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("PASSWORD_REQUIRED", "앱 비밀번호를 입력하세요");
        }
        if (!cipher.isConfigured()) {
            throw ApiException.badRequest("CRYPTO_DISABLED",
                    "서버 암호화 키(MAIL_CRED_KEY)가 설정되지 않아 저장할 수 없습니다. 관리자에게 문의하세요");
        }
        String name = req.name() == null || req.name().isBlank() ? null : req.name().trim();
        u.updateMailSender(email, cipher.encrypt(password), name);
        return MailSenderResponse.from(u);
    }

    private User load(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user not found"));
    }
}
