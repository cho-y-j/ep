package com.skep.bootstrap;

import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds predictable test accounts (one per role) when skep.bootstrap.test-users.enabled=true.
 * Off by default — enable only for local/dev stacks. Idempotent.
 */
@Configuration
public class TestUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(TestUserSeeder.class);

    private record SeedSpec(String email, String name, Role role) {}

    private static final List<SeedSpec> SEEDS = List.of(
            new SeedSpec("bp1@example.com", "테스트 BP", Role.BP),
            new SeedSpec("equipment1@example.com", "테스트 장비공급사", Role.EQUIPMENT_SUPPLIER),
            new SeedSpec("manpower1@example.com", "테스트 인력공급사", Role.MANPOWER_SUPPLIER)
    );

    @Bean
    public ApplicationRunner seedTestUsers(
            @Value("${skep.bootstrap.test-users.enabled:false}") boolean enabled,
            @Value("${skep.bootstrap.test-users.password:testpass123}") String password,
            UserRepository users,
            PasswordEncoder encoder
    ) {
        return args -> {
            if (!enabled) return;
            for (SeedSpec seed : SEEDS) {
                upsert(seed, password, users, encoder);
            }
        };
    }

    @Transactional
    void upsert(SeedSpec seed, String password, UserRepository users, PasswordEncoder encoder) {
        if (users.existsByEmail(seed.email())) {
            log.info("test seed already exists: {}", seed.email());
            return;
        }
        User u = User.builder()
                .email(seed.email())
                .password(encoder.encode(password))
                .name(seed.name())
                .role(seed.role())
                .isCompanyAdmin(false)
                .enabled(true)
                .build();
        users.save(u);
        log.info("test seed CREATED: {} ({})", seed.email(), seed.role());
    }
}
