package com.paullomaggio.estevaoLanches.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // 🚀 Ativa o gerenciador de mensagens em tempo real
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilita os canais de transmissão (os tópicos que o Caixa e Cozinha vão escutar)
        config.enableSimpleBroker("/topic");

        // Prefixo para mensagens que saem do Front e vão para métodos @MessageMapping no Back (se houver)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Define a URL que o sistema Web (Caixa/Cozinha) vai usar para se conectar ao servidor
        registry.addEndpoint("/ws-tevao")
                .setAllowedOrigins("*"); // Permite conexões de qualquer IP (crucial para o mobile/rede interna)

        // Fallback caso o navegador antigo não suporte WebSockets nativos
        registry.addEndpoint("/ws-tevao")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}