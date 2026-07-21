package com.skep.bootstrap;

import com.skep.clientorg.ClientOrg;
import com.skep.clientorg.ClientOrgRepository;
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

/**
 * 초기 온보딩 5계정 시드 — 관리자/장비/인력/BP/원청. 즉시 로그인 가능(enabled=true).
 * 멱등: 이메일/사업자번호/원청코드 존재 시 skip. env 플래그 SKEP_SEED_ONBOARDING=true 일 때만 동작.
 *
 * TestUserSeeder/DemoDataSeeder 와 달리 prod 프로필에서도 플래그가 켜지면 시드한다
 * (테스트 더미가 아니라 실제 온보딩 계정이므로). 비번은 운영에서 반드시 변경할 것.
 */
@Configuration
public class OnboardingAccountSeeder {

    private static final Logger log = LoggerFactory.getLogger(OnboardingAccountSeeder.class);

    @Bean
    public ApplicationRunner seedOnboardingAccounts(
            @Value("${SKEP_SEED_ONBOARDING:false}") boolean enabled,
            UserRepository users,
            CompanyRepository companies,
            ClientOrgRepository clientOrgs,
            PasswordEncoder encoder) {
        return args -> {
            if (!enabled) return;

            // 1) 수퍼어드민 — 회사/원청 불필요.
            upsertUser(users, encoder, "admin@one.on1.com", "피콘 관리자", "admin2505!",
                    Role.ADMIN, null, null, false);

            // 2) 장비임대사업자 — 회사 master.
            Long eqId = ensureCompany(companies, "701-81-25051", "피콘장비",
                    CompanyType.fromRole(Role.EQUIPMENT_SUPPLIER));
            upsertUser(users, encoder, "pcon1@pcon.com", "피콘장비 담당자", "admin1234",
                    Role.EQUIPMENT_SUPPLIER, eqId, null, true);

            // 3) 인력사업자 — 회사 master.
            Long mpId = ensureCompany(companies, "702-81-25052", "피콘인력",
                    CompanyType.fromRole(Role.MANPOWER_SUPPLIER));
            upsertUser(users, encoder, "pcon2@pcon.com", "피콘인력 담당자", "admin1234",
                    Role.MANPOWER_SUPPLIER, mpId, null, true);

            // 4) 비피사 — 회사 master.
            Long bpId = ensureCompany(companies, "703-81-25053", "피콘건설BP",
                    CompanyType.fromRole(Role.BP));
            upsertUser(users, encoder, "bp@pcon.com", "피콘BP 담당자", "admin1234",
                    Role.BP, bpId, null, true);

            // 5) 시행사/원청 — client_org 소속(읽기전용), 회사 없음.
            Long epId = ensureClientOrg(clientOrgs, "PCON", "피콘시행");
            upsertUser(users, encoder, "ep@pcon.com", "피콘시행 담당자", "admin1234",
                    Role.CLIENT, null, epId, false);
        };
    }

    private Long ensureCompany(CompanyRepository companies, String businessNumber, String name, CompanyType type) {
        Company company = companies.findByBusinessNumber(businessNumber)
                .orElseGet(() -> {
                    Company c = companies.save(Company.builder()
                            .name(name).businessNumber(businessNumber).type(type).build());
                    log.info("onboarding seed company CREATED: {} ({})", c.getName(), c.getType());
                    return c;
                });
        return company.getId();
    }

    private Long ensureClientOrg(ClientOrgRepository clientOrgs, String code, String name) {
        ClientOrg org = clientOrgs.findByCode(code)
                .orElseGet(() -> {
                    ClientOrg c = clientOrgs.save(ClientOrg.builder().name(name).code(code).build());
                    log.info("onboarding seed client_org CREATED: {} ({})", c.getName(), c.getCode());
                    return c;
                });
        return org.getId();
    }

    private void upsertUser(UserRepository users, PasswordEncoder encoder,
                            String email, String name, String rawPassword,
                            Role role, Long companyId, Long clientOrgId, boolean isCompanyAdmin) {
        if (users.existsByEmail(email)) {
            log.info("onboarding seed user already exists: {}", email);
            return;
        }
        users.save(User.builder()
                .email(email)
                .password(encoder.encode(rawPassword))
                .name(name)
                .role(role)
                .companyId(companyId)
                .clientOrgId(clientOrgId)
                .isCompanyAdmin(isCompanyAdmin)
                .enabled(true)
                .build());
        log.info("onboarding seed user CREATED: {} ({})", email, role);
    }
}
