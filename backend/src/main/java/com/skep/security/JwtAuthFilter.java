package com.skep.security;

import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, @Lazy UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                Long userId = Long.valueOf(claims.getSubject());

                // disabled 계정의 access token 즉시 무효화 — 비활성 + 비번 변경 직후 잔여 access token 차단.
                User u = users.findById(userId).orElse(null);
                if (u == null || !u.isEnabled()) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }

                Role role = Role.valueOf(claims.get("role", String.class));
                String email = claims.get("email", String.class);
                String name = claims.get("name", String.class);
                Number companyIdNum = claims.get("company_id", Number.class);
                Long companyId = companyIdNum != null ? companyIdNum.longValue() : null;
                Boolean isCompanyAdminClaim = claims.get("is_company_admin", Boolean.class);
                boolean isCompanyAdmin = isCompanyAdminClaim != null && isCompanyAdminClaim;

                AuthenticatedUser principal = new AuthenticatedUser(userId, email, name, role, companyId, isCompanyAdmin);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
