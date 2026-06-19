package com.paullomaggio.estevaoLanches.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityFilter securityFilter;

    public SecurityConfig(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Libera as checagens automáticas do navegador (OPTIONS) sem exigir token JWT
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()

                        // Rotas públicas de Autenticação
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cliente/google").permitAll()
                        .requestMatchers("/error").permitAll()

                        // PONTE DE IMPRESSÃO LIBERADA
                        .requestMatchers("/api/fila-impressao/**").permitAll()
                        .requestMatchers("/api/pedidos/fila-impressao/**").permitAll()

                        // 🚀 ALTERADO: Nova porta independente criada para o gerenciamento de mesas no celular
                        .requestMatchers("/api/comandas/**").hasAnyRole("ADMIN", "GARCOM")

                        // CONTROLE DO CAIXA
                        .requestMatchers(HttpMethod.GET, "/api/caixas/status").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers(HttpMethod.GET, "/api/caixas/resumo").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers("/api/caixas/**").hasRole("ADMIN")

                        // TRAVAS DO GERENTE (ADMIN)
                        .requestMatchers("/api/relatorios/**").hasRole("ADMIN")
                        .requestMatchers("/api/cardapio/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")

                        // ROTAS OPERACIONAIS
                        .requestMatchers("/api/pedidos/checkout").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers("/api/pedidos/**").hasAnyRole("ADMIN", "GARCOM", "COZINHA")
                        .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN", "GARCOM")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}