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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    public ApplicationRunner bootstrapAdmin(UserRepository users,
                                            PasswordEncoder encoder,
                                            BootstrapAdminProperties props) {
        return args -> ensureAdmin(users, encoder, props);
    }

    @Transactional
    void ensureAdmin(UserRepository users, PasswordEncoder encoder, BootstrapAdminProperties props) {
        if (users.existsByEmail(props.email())) {
            log.info("bootstrap admin already exists: {}", props.email());
            return;
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
}
