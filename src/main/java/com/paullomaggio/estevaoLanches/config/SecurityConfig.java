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
                // Retorna 401 Unauthorized para acessos sem token ou com token inválido
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

                        // PORTAS PÚBLICAS: Hubs de Impressão e WebSockets
                        .requestMatchers("/api/fila-impressao/**").permitAll()
                        .requestMatchers("/api/pedidos/fila-impressao/**").permitAll()
                        .requestMatchers("/ws-tevao/**").permitAll()

                        // === NOVO DOMÍNIO CONTROLLER V2.0: SESSÃO DA MESA MOBILE ===
                        .requestMatchers("/api/v1/garcom/**").hasAnyAuthority("ROLE_GARCOM", "ROLE_ADMIN")

                        // APP DE DELIVERY
                        .requestMatchers("/api/delivery/pedidos/**").hasAnyAuthority("ROLE_CLIENTE", "ROLE_ADMIN")

                        // COMPATIBILIDADE OPERACIONAL DO SALÃO (RETIRE GRADUALMENTE APÓS FASE 4)
                        .requestMatchers("/api/comandas/**").hasAnyAuthority("ROLE_GARCOM", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/caixas/status").hasAnyAuthority("ROLE_GARCOM", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/caixas/resumo").hasAnyAuthority("ROLE_GARCOM", "ROLE_ADMIN")
                        .requestMatchers("/api/pedidos/balcao/checkout").hasAnyAuthority("ROLE_GARCOM", "ROLE_ADMIN")
                        .requestMatchers("/api/clientes/**").hasAnyAuthority("ROLE_GARCOM", "ROLE_ADMIN")
                        .requestMatchers("/api/contas/**").hasAnyAuthority("ROLE_GARCOM", "ROLE_ADMIN")

                        // ESTEIRA DE PRODUÇÃO COZINHA
                        .requestMatchers("/api/pedidos/**").hasAnyAuthority("ROLE_COZINHA", "ROLE_GARCOM", "ROLE_ADMIN")

                        // ACESSO ADMINISTRATIVO GERENCIAL
                        .requestMatchers("/api/caixas/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/relatorios/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/produtos/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_GARCOM", "ROLE_CLIENTE")
                        .requestMatchers(HttpMethod.POST, "/api/produtos/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/produtos/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/produtos/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/usuarios/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/pagamentos/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/carrinhos/**").hasAnyAuthority("ROLE_CLIENTE", "ROLE_ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("https://estevao-lanches-front.vercel.app", "https://estevao-lanches-front-mqv8gtd3m-estevaoadmin-6034s-projects.vercel.app", "https://localhost"));
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