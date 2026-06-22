package com.paullomaggio.estevaoLanches.config;

import com.paullomaggio.estevaoLanches.config.SecurityFilter;
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
                        // 🎯 FIX REQUISIÇÕES OPTIONS: Removido o 'CorsUtils::isPreFlightRequest'
                        // Como a configuração de CORS injetada abaixo já lida nativamente com o método OPTIONS,
                        // declarar essa validação na esteira de requisições do filtro torna-se redundante.

                        // 🔓 PORTAS PÚBLICAS: Autenticação do Ecossistema (Nativo + Delivery App)
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login/cliente").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registrar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cliente/google").permitAll()
                        .requestMatchers("/error").permitAll()

                        // 🔓 PORTAS PÚBLICAS: Hubs da Ponte Física de Impressão Térmica
                        .requestMatchers("/api/fila-impressao/**").permitAll()
                        .requestMatchers("/api/pedidos/fila-impressao/**").permitAll()

                        // 🎯 FIX CRÍTICO: Canal de WebSocket totalmente liberado da verificação do filtro JWT.
                        // O upgrade de protocolo HTTP para WebSocket não envia Headers Bearer tradicionais na largada.
                        .requestMatchers("/ws-tevao/**").permitAll()

                        // 👥 PAPEL MISTO (ADMIN ou GARÇOM): Operação em Tempo Real do Salão
                        .requestMatchers("/api/comandas/**").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers(HttpMethod.GET, "/api/caixas/status").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers(HttpMethod.GET, "/api/caixas/resumo").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers("/api/pedidos/checkout").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN", "GARCOM")

                        // 👥 PAPEL OPERACIONAL DA ESTEIRA: Produção de Cozinha e Expedição
                        .requestMatchers("/api/pedidos/**").hasAnyRole("ADMIN", "GARCOM", "COZINHA")

                        // 🔒 PAPEL EXCLUSIVO DO GERENTE (ADMIN): Auditoria e Configurações Sensíveis
                        .requestMatchers("/api/caixas/**").hasRole("ADMIN")
                        .requestMatchers("/api/relatorios/**").hasRole("ADMIN")
                        .requestMatchers("/api/cardapio/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")

                        // Qualquer rota residual exige validação de token
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