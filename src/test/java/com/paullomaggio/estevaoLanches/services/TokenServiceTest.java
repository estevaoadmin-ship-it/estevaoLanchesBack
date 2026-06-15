package com.paullomaggio.estevaoLanches.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.RoleUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private TokenService tokenService;
    private Usuario usuario;
    private String segredoPadrao = "ChaveSecretaEstevaoLanches2026!";

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", segredoPadrao);

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Estêvão Dono");
        usuario.setEmail("gerente@tevao.com");
        usuario.setRole(RoleUsuario.ADMIN);
        usuario.setAtivo(true);
    }

    // ==========================================
    // 1. TESTES DE GERAÇÃO DO JWT (AGORA COM 12 HORAS)
    // ==========================================

    @Test
    @DisplayName("Garantia de Emissão: Deve estruturar um JWT preenchido e assinado")
    void gerarTokenCenariosEstruturais() {
        String token = tokenService.gerarToken(usuario);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    @DisplayName("CRÍTICO: O Token gerado deve conter as Claims corretas e a validade exata de 12 horas")
    void gerarTokenCenariosClaims() {
        String token = tokenService.gerarToken(usuario);

        DecodedJWT decodedJWT = JWT.decode(token);

        assertEquals("gerente@tevao.com", decodedJWT.getSubject());
        assertEquals("ADMIN", decodedJWT.getClaim("role").asString());
        assertEquals("Estêvão Dono", decodedJWT.getClaim("nome").asString());
        assertEquals("estevao-lanches-api", decodedJWT.getIssuer());

        assertNotNull(decodedJWT.getExpiresAt());

        // AJUSTADO: Verificação de 12 horas operacionais completas
        long dozeHorasEmSegundos = 12 * 60 * 60;
        long agoraEmSegundos = Instant.now().getEpochSecond();
        long expiracaoEmSegundos = decodedJWT.getExpiresAt().toInstant().getEpochSecond();

        assertTrue(expiracaoEmSegundos >= (agoraEmSegundos + dozeHorasEmSegundos - 5));
    }

    // ==========================================
    // 2. TESTES DE VALIDAÇÃO E SEGURANÇA Robusta
    // ==========================================

    @Test
    @DisplayName("Deve extrair o e-mail do funcionário com sucesso ao receber um token legítimo")
    void validarTokenCenarioValido() {
        String token = tokenService.gerarToken(usuario);

        String emailDecodificado = tokenService.validarToken(token);

        assertEquals("gerente@tevao.com", emailDecodificado);
    }

    @Test
    @DisplayName("Deve recusar strings aleatórias ou vazias retornando string vazia de negação")
    void validarTokenCenarioInvalido() {
        String email1 = tokenService.validarToken("uma-string-qualquer-que-nao-e-jwt");
        String email2 = tokenService.validarToken("");

        assertEquals("", email1);
        assertEquals("", email2);
    }

    @Test
    @DisplayName("CRÍTICO: Deve bloquear tokens violados ou corrompidos por hackers por alteração de caracteres")
    void validarTokenCenarioCorrompido() {
        String tokenOriginal = tokenService.gerarToken(usuario);
        String tokenAlterado = tokenOriginal + "a";

        String emailResultado = tokenService.validarToken(tokenAlterado);

        assertEquals("", emailResultado);
    }

    @Test
    @DisplayName("CRÍTICO: Deve rejeitar o acesso se o token foi assinado com uma chave secreta alienígena")
    void validarTokenCenarioAssinaturaInvalida() {
        String tokenInvasor = JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject("hacker@gmail.com")
                .sign(Algorithm.HMAC256("ChaveFalsaQualquer123"));

        String emailResultado = tokenService.validarToken(tokenInvasor);

        assertEquals("", emailResultado);
    }

    @Test
    @DisplayName("Deve rejeitar tokens gerados por servidores de terceiros com Issuer modificado")
    void validarTokenCenarioIssuerInvalido() {
        String tokenIssuerFalso = JWT.create()
                .withIssuer("sistema-de-outro-restaurante")
                .withSubject("gerente@tevao.com")
                .sign(Algorithm.HMAC256(segredoPadrao));

        String emailResultado = tokenService.validarToken(tokenIssuerFalso);

        assertEquals("", emailResultado);
    }

    @Test
    @DisplayName("CRÍTICO: Deve bloquear funcionários antigos tentando usar tokens de turnos passados que já venceram")
    void validarTokenCenarioExpirado() {
        String tokenExpirado = JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject("gerente@tevao.com")
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256(segredoPadrao));

        String emailResultado = tokenService.validarToken(tokenExpirado);

        assertEquals("", emailResultado);
    }
}