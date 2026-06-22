package com.paullomaggio.estevaoLanches.config;

import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import com.paullomaggio.estevaoLanches.repositories.UsuarioRepository;
import com.paullomaggio.estevaoLanches.services.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;
    private final ContaDeliveryRepository contaDeliveryRepository;

    public SecurityFilter(TokenService tokenService,
                          UsuarioRepository usuarioRepository,
                          ContaDeliveryRepository contaDeliveryRepository) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.contaDeliveryRepository = contaDeliveryRepository;
    }

    // 🎯 O FILTRO DE BARREIRA: Diz ao Spring quais rotas estão terminantemente dispensadas de validação JWT
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();

        // Bloqueia a execução do filtro para as portas públicas de autenticação, ponte de impressão e WebSocket
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/fila-impressao")
                || path.startsWith("/api/pedidos/fila-impressao")
                || path.startsWith("/ws-tevao")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = recuperarToken(request);

        if (token != null) {
            String email = tokenService.validarToken(token);
            String tipoConta = tokenService.extrairTipoConta(token);

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

    private UserDetails buscarUsuarioPorTipo(String email, String tipoConta) {
        return switch (tipoConta) {
            case "COLABORADOR" -> usuarioRepository.findByEmail(email).orElse(null);
            case "CLIENTE" -> contaDeliveryRepository.findByEmail(email).orElse(null);
            default -> null;
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