package com.skep.bootstrap;

import com.skep.config.BootstrapAdminProperties;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    // 운영 환경 약비번 차단 — 최소 강도 규칙. 블록리스트(admin1234! 처럼 접미사 회피) 대신 규칙 검증.
    private static final int MIN_PROD_PASSWORD_LENGTH = 12;

    @Bean
    public ApplicationRunner bootstrapAdmin(UserRepository users,
                                            PasswordEncoder encoder,
                                            BootstrapAdminProperties props,
                                            Environment env) {
        return args -> ensureAdmin(users, encoder, props, env);
    }

    @Transactional
    void ensureAdmin(UserRepository users, PasswordEncoder encoder, BootstrapAdminProperties props, Environment env) {
        if (users.existsByEmail(props.email())) {
            log.info("bootstrap admin already exists: {}", props.email());
            return;
        }
        // admin 을 실제로 새로 생성할 때만 prod 약비번 차단. 이미 admin 이 있으면 이 비번은 미사용(dead config)
        // 이므로 부팅을 막지 않는다 — 운영 중인 시스템이 환경변수 값 때문에 크래시루프에 빠지는 것 방지.
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (isProd && isWeakPassword(props.password())) {
            throw new IllegalStateException(
                    "SKEP_BOOTSTRAP_ADMIN_PASSWORD is too weak for prod — need >=" + MIN_PROD_PASSWORD_LENGTH
                    + " chars with upper, lower, digit, and symbol");
        }
        User admin = User.builder()
                .email(props.email())
                .password(encoder.encode(props.password()))
                .name(props.name())
                .role(Role.ADMIN)
                .isCompanyAdmin(false)
                .enabled(true)
                .build();
        users.save(admin);
        log.warn("bootstrap admin CREATED: {} — change password immediately", props.email());
    }

    /** prod 약비번 차단 — 길이/문자종류(대/소/숫자/기호) 규칙. 접미사 회피(admin1234!)도 길이로 걸러짐. */
    static boolean isWeakPassword(String pw) {
        if (pw == null || pw.length() < MIN_PROD_PASSWORD_LENGTH) return true;
        boolean upper = false, lower = false, digit = false, symbol = false;
        for (int i = 0; i < pw.length(); i++) {
            char c = pw.charAt(i);
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else symbol = true;
        }
        return !(upper && lower && digit && symbol);
    }
}
