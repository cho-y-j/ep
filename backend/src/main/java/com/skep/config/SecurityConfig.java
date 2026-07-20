package com.skep.config;

import com.skep.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsProperties corsProps;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, CorsProperties corsProps) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsProps = corsProps;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/onlyoffice/work-plan/*/file").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/onlyoffice/work-plan/*/file.docx").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/onlyoffice/work-plan/*/file").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/onlyoffice/work-plan/*/file.docx").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/onlyoffice/work-plan/*/callback").permitAll()
                        // S-12: 작업계획서 생성 중 임시 OnlyOffice 세션 (wp.id 없이 동작) — OnlyOffice 컨테이너가 호출.
                        .requestMatchers(HttpMethod.GET, "/api/worksheet/editor-file/*").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/worksheet/editor-file/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/worksheet/onlyoffice-callback/*").permitAll()
                        // S-12: 전자서명 — 토큰 기반, 비로그인 접근.
                        .requestMatchers(HttpMethod.GET, "/api/sign/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sign/*/pdf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sign/*").permitAll()
                        // 서류 수집 공개 링크 — 토큰 기반, 비로그인 접근.
                        .requestMatchers(HttpMethod.GET, "/api/collect/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/collect/*/documents").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/collect/*/submit").permitAll()
                        // V116: 서류종류 '샘플 보기' 예시 이미지 — 마스킹된 예시(민감정보 아님), 비로그인 공개.
                        .requestMatchers(HttpMethod.GET, "/api/document-types/*/sample").permitAll()
                        // skep-app(현장 작업자): 작업자 엔드포인트는 X-Field-Token(서비스 검증).
                        // announcements/attendance/today/safety-alerts(ADMIN)는 아래 anyRequest().authenticated() + @PreAuthorize 로 JWT 보호.
                        // skep-v2 근무자 코드 인증 (/api/field-auth/**) — X-Field-Token 헤더로 컨트롤러가 직접 검증.
                        .requestMatchers("/api/field-auth/**").permitAll()
                        // STOMP WebSocket — HTTP 핸드셰이크는 permitAll. 인증/인가는 STOMP CONNECT·SUBSCRIBE
                        // 프레임에서 StompAuthChannelInterceptor 가 JWT 로 수행 (안전알림 토픽 보호).
                        .requestMatchers("/ws/**", "/ws-raw/**").permitAll()
                        // P2c: 원청(CLIENT) 읽기전용 관제 API — CLIENT 전용.
                        .requestMatchers("/api/client/**").hasRole("CLIENT")
                        // P3d: 안전관리 이행 보고서 — BP·ADMIN·CLIENT 공용(서비스에서 현장 스코프 재검증). CLIENT 전역 차단의 예외.
                        .requestMatchers(HttpMethod.GET, "/api/safety-reports").hasAnyRole("ADMIN", "BP", "CLIENT")
                        // P4a: 안전 상황판 — BP·ADMIN·CLIENT 공용(서비스에서 현장 스코프 재검증). CLIENT 전역 차단의 예외.
                        .requestMatchers("/api/safety-board/**").hasAnyRole("ADMIN", "BP", "CLIENT")
                        // CLIENT 도 접근 가능한 공용(본인 스코프) 엔드포인트 — 로그인 세션·알림.
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        // 그 외 모든 /api/** : 인증 필수 + CLIENT 차단(기존 도메인 컨트롤러 접근 불가 — §1.1 격리).
                        // 기존 역할(ADMIN/BP/공급사/WORKER)의 인가 동작은 종전과 동일.
                        .anyRequest().access((authentication, context) -> {
                            Authentication a = authentication.get();
                            boolean authed = a != null && a.isAuthenticated()
                                    && !(a instanceof AnonymousAuthenticationToken);
                            boolean isClient = authed && a.getAuthorities().stream()
                                    .anyMatch(g -> "ROLE_CLIENT".equals(g.getAuthority()));
                            return new AuthorizationDecision(authed && !isClient);
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(corsProps.allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
