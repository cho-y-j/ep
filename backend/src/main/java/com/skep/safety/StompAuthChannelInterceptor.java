package com.skep.safety;

import com.skep.security.AuthenticatedUser;
import com.skep.security.JwtService;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP 메시지 레벨 인가. 안전알림 토픽은 작업자 PII·건강·위치를 발행하므로
 * CONNECT 시 JWT 를 검증해 user 를 stamp 하고(Spring 이 이후 프레임에 전파),
 * SUBSCRIBE 시 목적지를 actor 의 role/회사로 제한한다.
 *  - ADMIN: 모든 안전알림 토픽 구독 가능
 *  - BP: 자기 회사(company-{companyId}) 토픽만
 *  - 그 외 role / 무인증: 안전알림 토픽 구독 거부
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ALERT_TOPIC_PREFIX = "/topic/safety-alerts/";
    private static final String COMPANY_TOPIC_PREFIX = "/topic/safety-alerts/company-";

    private final JwtService jwtService;
    private final UserRepository users;

    public StompAuthChannelInterceptor(JwtService jwtService, @Lazy UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            AuthenticatedUser principal = authenticate(accessor);
            if (principal == null) {
                throw new IllegalArgumentException("WS_UNAUTHENTICATED");
            }
            // user header stamp — Spring 이 같은 세션의 이후 프레임에 자동 전파.
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private AuthenticatedUser authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            User u = users.findById(userId).orElse(null);
            if (u == null || !u.isEnabled()) {
                return null;
            }
            Role role = Role.valueOf(claims.get("role", String.class));
            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);
            Number companyIdNum = claims.get("company_id", Number.class);
            Long companyId = companyIdNum != null ? companyIdNum.longValue() : null;
            Boolean isCompanyAdminClaim = claims.get("is_company_admin", Boolean.class);
            boolean isCompanyAdmin = isCompanyAdminClaim != null && isCompanyAdminClaim;
            return new AuthenticatedUser(userId, email, name, role, companyId, isCompanyAdmin);
        } catch (Exception ex) {
            return null;
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null || !dest.startsWith(ALERT_TOPIC_PREFIX)) {
            // 안전알림 외 토픽은 발행하지 않으므로 이 인터셉터의 관심사가 아님.
            return;
        }
        AuthenticatedUser actor = currentActor(accessor);
        if (actor == null) {
            throw new IllegalArgumentException("WS_UNAUTHENTICATED");
        }
        if (actor.role() == Role.ADMIN) {
            return; // ADMIN 은 모든 안전알림 토픽 허용.
        }
        if (actor.role() == Role.BP && actor.companyId() != null
                && dest.equals(COMPANY_TOPIC_PREFIX + actor.companyId())) {
            return; // BP 는 자기 회사 토픽만.
        }
        throw new IllegalArgumentException("WS_FORBIDDEN_TOPIC");
    }

    private AuthenticatedUser currentActor(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser actor) {
            return actor;
        }
        return null;
    }
}
