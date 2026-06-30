package com.paullomaggio.estevaoLanches.config;

import com.paullomaggio.estevaoLanches.config.SecurityFilter;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/swagger/**"
    };

    public SecurityConfig(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ✅ Resolve CT-INT-019: Retorna 401 Unauthorized para acessos sem token
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Não autorizado");
                        })
                )
                .authorizeHttpRequests(authorize -> authorize
                        // PORTAS PÚBLICAS: Autenticação do Ecossistema
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login/cliente").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registrar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/cliente/google").permitAll()
                        .requestMatchers("/error").permitAll()

                        // PORTAS PÚBLICAS: Swagger
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()

                        // PORTAS PÚBLICAS: Hubs da Ponte Física de Impressão Térmica
                        .requestMatchers("/api/fila-impressao/**").permitAll()
                        .requestMatchers("/api/pedidos/fila-impressao/**").permitAll()

                        // Rota do WebSocket liberada
                        .requestMatchers("/ws-tevao/**").permitAll()

                        // === NOVO: APP DE DELIVERY ===
                        // Restringe as rotas do aplicativo aos clientes autenticados (e admins)
                        .requestMatchers("/api/delivery/pedidos/**").hasAnyAuthority("ROLE_CLIENTE", "ROLE_ADMIN")

                        // PAPEL MISTO (ADMIN ou GARÇOM): Operação em Tempo Real do Salão
                        .requestMatchers("/api/comandas/**").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers(HttpMethod.GET, "/api/caixas/status").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers(HttpMethod.GET, "/api/caixas/resumo").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers("/api/pedidos/balcao/checkout").hasAnyRole("ADMIN", "GARCOM") // Rota ajustada
                        .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN", "GARCOM")
                        .requestMatchers("/api/contas/**").hasAnyRole("ADMIN", "GARCOM") // ✅ Adicionado proteção para /api/contas

                        // PAPEL OPERACIONAL DA ESTEIRA: Produção de Cozinha e Expedição
                        .requestMatchers("/api/pedidos/**").hasAnyRole("ADMIN", "GARCOM", "COZINHA")

                        // PAPEL EXCLUSIVO DO GERENTE (ADMIN)
                        .requestMatchers("/api/caixas/**").hasRole("ADMIN")
                        .requestMatchers("/api/relatorios/**").hasRole("ADMIN")
                        // Regras de autorização para /api/produtos
                        .requestMatchers(HttpMethod.GET, "/api/produtos/**").hasAnyRole("ADMIN", "GARCOM", "CLIENTE")
                        .requestMatchers(HttpMethod.POST, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/pagamentos/**").hasRole("ADMIN") // ✅ Adicionado proteção para /api/pagamentos
                        .requestMatchers("/api/carrinhos/**").hasAnyRole("CLIENTE", "ADMIN") // ✅ Adicionado proteção para /api/carrinhos

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
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

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