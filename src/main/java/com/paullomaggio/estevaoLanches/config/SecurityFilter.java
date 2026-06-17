package com.paullomaggio.estevaoLanches.config;

import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.UsuarioRepository;
import com.paullomaggio.estevaoLanches.services.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = recuperarToken(request);

        if (token != null) {
            String email = tokenService.validarToken(token);
            String tipoConta = tokenService.extrairTipoConta(token);

            // Ajuste: Adicionado verificação de nulo no email por segurança extra
            if (email != null && !email.isEmpty() && tipoConta != null) {
                UserDetails userDetails = buscarUsuarioPorTipo(email, tipoConta);

                if (userDetails != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // Estratégia de Roteamento O(1)
    private UserDetails buscarUsuarioPorTipo(String email, String tipoConta) {
        // Nota: Esta sintaxe de switch (com ->) exige Java 14 ou superior.
        // Como o Spring Boot 3 usa Java 17+, funcionará perfeitamente.
        return switch (tipoConta) {
            case "COLABORADOR" -> usuarioRepository.findByEmail(email).orElse(null);
            case "CLIENTE" -> clienteRepository.findByEmail(email).orElse(null);
            default -> null; // Facilmente extensível no futuro (ex: MOTOBOY)
        };
    }

    private String recuperarToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.replace("Bearer ", "");
    }
}