package com.paullomaggio.estevaoLanches.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    public String gerarToken(Usuario usuario) {
        return JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject(usuario.getEmail())
                // CORRECAO: Como 'role' ja e uma String na entidade Usuario, removemos o .name()
                .withClaim("role", usuario.getRole())
                .withClaim("nome", usuario.getNome())
                .withClaim("tipo_conta", "COLABORADOR")
                .withExpiresAt(gerarDataExpiracao())
                .sign(Algorithm.HMAC256(secret));
    }

    public String gerarTokenCliente(ContaDelivery conta) {
        return JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject(conta.getEmail())
                .withClaim("role", conta.getRole())
                .withClaim("nome", conta.getCliente().getNome()) // Preserva o nome comercial no token
                .withClaim("tipo_conta", "CLIENTE")
                .withExpiresAt(gerarDataExpiracao())
                .sign(Algorithm.HMAC256(secret));
    }

    public String validarToken(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("estevao-lanches-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (Exception e) {
            return "";
        }
    }

    public String extrairTipoConta(String token) {
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer("estevao-lanches-api")
                    .build()
                    .verify(token);

            var claim = jwt.getClaim("tipo_conta");
            return (claim.isMissing() || claim.isNull()) ? "COLABORADOR" : claim.asString();
        } catch (Exception e) {
            return null;
        }
    }

    private Instant gerarDataExpiracao() {
        return LocalDateTime.now().plusHours(12).toInstant(ZoneOffset.of("-03:00"));
    }
}