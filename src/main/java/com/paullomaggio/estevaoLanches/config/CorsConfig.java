package com.paullomaggio.estevaoLanches.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Permite credenciais (necessário para tráfego seguro de tokens em pacotes mobile)
        config.setAllowCredentials(true);

        // Libera padrões de origem para aceitar o mapeamento dinâmico do Ngrok e do Capacitor (https://localhost)
        config.setAllowedOriginPatterns(Collections.singletonList("*"));

        // Libera explicitamente todos os métodos HTTP requisitados pelo app
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 🎯 O CORAÇÃO DO FIX: Libera explicitamente o cabeçalho de Authorization que o Android barrou
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "X-Requested-With"));

        // Garante que o app consiga ler o cabeçalho de Authorization na resposta
        config.setExposedHeaders(Collections.singletonList("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}