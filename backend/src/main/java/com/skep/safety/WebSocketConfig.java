package com.skep.safety;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP over WebSocket — /ws 엔드포인트, /topic 브로커. ADMIN/BP 가 안전알림 실시간 구독. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        // 클라이언트→서버 송신 목적지(/app) 는 사용하지 않음 — 서버 발행 전용 토픽만.
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT 시 JWT 검증 + SUBSCRIBE 시 토픽을 actor role/회사로 인가.
        registration.interceptors(authInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        // SockJS 없는 raw WebSocket 도 허용 (네이티브 STOMP 클라이언트용).
        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*");
    }
}
