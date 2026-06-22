package com.paullomaggio.estevaoLanches.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 🎯 FIX: Substituído '.setAllowedOrigins("*")' por '.setAllowedOriginPatterns("*")'
        // Em versões modernas do Spring Framework, o uso de wildcards brutos com credenciais ativas
        // causa colisão de políticas de CORS. O OriginPatterns soluciona a comunicação com apps híbridos.
        registry.addEndpoint("/ws-tevao")
                .setAllowedOriginPatterns("*");

        registry.addEndpoint("/ws-tevao")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}