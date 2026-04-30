package com.skep.bootstrap;

import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
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
 * Seeds predictable test accounts (one per role) plus a company per supplier-type seed.
 * Idempotent — safe to run on every boot. Off by default; enable via env in dev/local.
 */
@Configuration
public class TestUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(TestUserSeeder.class);

    private record SeedSpec(
            String email,
            String userName,
            Role role,
            String companyName,
            String businessNumber,
            CompanyType companyType
    ) {}

    private static final List<SeedSpec> SEEDS = List.of(
            new SeedSpec("bp1@example.com", "테스트 BP", Role.BP,
                    "테스트 BP건설(주)", "111-11-11111", CompanyType.BP),
            new SeedSpec("equipment1@example.com", "테스트 장비공급사", Role.EQUIPMENT_SUPPLIER,
                    "테스트 장비공급(주)", "222-22-22222", CompanyType.EQUIPMENT),
            new SeedSpec("manpower1@example.com", "테스트 인력공급사", Role.MANPOWER_SUPPLIER,
                    "테스트 인력공급(주)", "333-33-33333", CompanyType.MANPOWER)
    );

    @Bean
    public ApplicationRunner seedTestUsers(
            @Value("${skep.bootstrap.test-users.enabled:false}") boolean enabled,
            @Value("${skep.bootstrap.test-users.password:testpass123}") String password,
            UserRepository users,
            CompanyRepository companies,
            PasswordEncoder encoder
    ) {
        return args -> {
            if (!enabled) return;
            for (SeedSpec seed : SEEDS) {
                upsert(seed, password, users, companies, encoder);
            }
        };
    }

    @Transactional
    void upsert(SeedSpec seed, String password,
                UserRepository users, CompanyRepository companies, PasswordEncoder encoder) {
        Company company = companies.findByBusinessNumber(seed.businessNumber())
                .orElseGet(() -> {
                    Company c = companies.save(Company.builder()
                            .name(seed.companyName())
                            .businessNumber(seed.businessNumber())
                            .type(seed.companyType())
                            .build());
                    log.info("test seed company CREATED: {} ({})", c.getName(), c.getType());
                    return c;
                });

        if (users.existsByEmail(seed.email())) {
            log.info("test seed user already exists: {}", seed.email());
            return;
        }

        User u = User.builder()
                .email(seed.email())
                .password(encoder.encode(password))
                .name(seed.userName())
                .role(seed.role())
                .companyId(company.getId())
                .isCompanyAdmin(true)
                .enabled(true)
                .build();
        users.save(u);
        log.info("test seed user CREATED: {} ({}) -> company {}",
                seed.email(), seed.role(), company.getName());
    }
}
