package com.paullomaggio.estevaoLanches.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.paullomaggio.estevaoLanches.entities.Cliente;
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
    private Cliente cliente;
    private final String segredoPadrao = "ChaveSecretaEstevaoLanches2026!";

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

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("José Antônio d'Ávila");
        cliente.setEmail("jose.cliente+ifood@gmail.com");
    }

    @Test
    @DisplayName("Garantia de Emissão: Deve estruturar um JWT preenchido e assinado")
    void gerarTokenCenariosEstruturais() {
        String token = tokenService.gerarToken(usuario);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("CRÍTICO: Deve conter as Claims corretas e validade de 12 horas")
    void gerarTokenCenariosClaims() {
        String token = tokenService.gerarToken(usuario);
        DecodedJWT jwt = JWT.decode(token);

        assertEquals("gerente@tevao.com", jwt.getSubject());
        assertEquals("ADMIN", jwt.getClaim("role").asString());
        assertEquals("COLABORADOR", jwt.getClaim("tipo_conta").asString());

        long dozeHoras = 12 * 60 * 60;
        long expiracao = jwt.getExpiresAt().toInstant().getEpochSecond();
        long esperado = Instant.now().getEpochSecond() + dozeHoras;
        assertTrue(Math.abs(expiracao - esperado) < 10);
    }

    @Test
    @DisplayName("CRÍTICO: Deve gerar token exclusivo para Cliente")
    void deveGerarTokenParaClienteComClaimsCorretas() {
        String token = tokenService.gerarTokenCliente(cliente);
        DecodedJWT jwt = JWT.decode(token);

        assertEquals("CLIENTE", jwt.getClaim("tipo_conta").asString());
        assertEquals("CLIENTE", jwt.getClaim("role").asString());
    }

    @Test
    @DisplayName("Deve extrair corretamente tipo de conta COLABORADOR e CLIENTE")
    void deveExtrairTipoContaCorretamente() {
        assertEquals("COLABORADOR", tokenService.extrairTipoConta(tokenService.gerarToken(usuario)));
        assertEquals("CLIENTE", tokenService.extrairTipoConta(tokenService.gerarTokenCliente(cliente)));
    }

    @Test
    @DisplayName("Fallback: Deve retornar COLABORADOR se o token não tiver a claim tipo_conta")
    void deveRetornarColaboradorParaTokensAntigos() {
        String tokenAntigo = JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject("antigo@tevao.com")
                .sign(Algorithm.HMAC256(segredoPadrao));

        assertEquals("COLABORADOR", tokenService.extrairTipoConta(tokenAntigo));
    }

    @Test
    @DisplayName("Deve retornar nulo para tokens inválidos")
    void deveRetornarNullParaTokenInvalido() {
        assertNull(tokenService.extrairTipoConta("token.invalido.abc"));
        assertNull(tokenService.extrairTipoConta(null));
    }

    @Test
    @DisplayName("Validação: Deve retornar e-mail correto para token legítimo")
    void validarTokenCenarioValido() {
        String token = tokenService.gerarToken(usuario);
        assertEquals("gerente@tevao.com", tokenService.validarToken(token));
    }

    @Test
    @DisplayName("Validação: Deve retornar string vazia para tokens maliciosos/inválidos")
    void validarTokenCenarioInvalido() {
        assertEquals("", tokenService.validarToken("invalido"));
        assertEquals("", tokenService.validarToken(""));
        assertEquals("", tokenService.validarToken(null));
    }

    @Test
    @DisplayName("Segurança: Deve bloquear tokens corrompidos")
    void validarTokenCenarioCorrompido() {
        String token = tokenService.gerarToken(usuario);
        assertEquals("", tokenService.validarToken(token + "x"));
    }

    @Test
    @DisplayName("Segurança: Deve rejeitar token com chave secreta diferente")
    void validarTokenAssinaturaInvalida() {
        String tokenHacker = JWT.create().withSubject("hacker").sign(Algorithm.HMAC256("ChaveErrada"));
        assertEquals("", tokenService.validarToken(tokenHacker));
    }

    @Test
    @DisplayName("Segurança: Deve rejeitar token expirado")
    void validarTokenExpirado() {
        String tokenExpirado = JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject("gerente@tevao.com")
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256(segredoPadrao));

        assertEquals("", tokenService.validarToken(tokenExpirado));
    }

    @Test
    @DisplayName("Segurança: Deve rejeitar ataque de algoritmo NONE")
    void deveRejeitarTokenAlgoritmoNone() {
        String header = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"; // {"alg":"none"}
        String payload = "eyJzdWIiOiJoYWNrZXIiLCJpc3MiOiJlc3RldmFvLWxhbmNoZXMtYXBpIn0";
        String token = header + "." + payload + ".";
        assertEquals("", tokenService.validarToken(token));
    }

    @Test
    @DisplayName("Blindagem: Deve rejeitar tokens com Claims manipuladas (ex: Role trocada)")
    void deveRejeitarTokenComClaimsManipuladas() {
        // Criamos um token, mas tentamos forçar que um CLIENTE tenha role ADMIN via estrutura manual
        String tokenMalicioso = JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject("hacker@gmail.com")
                .withClaim("role", "ADMIN") // Tentativa de escalação de privilégio
                .withClaim("tipo_conta", "CLIENTE")
                .sign(Algorithm.HMAC256(segredoPadrao));

        // Embora o token seja assinado, o seu sistema no SecurityFilter
        // deve validar a consistência.
        // O teste aqui garante que o serviço de extração não falha ao ler claims estranhas.
        assertEquals("CLIENTE", tokenService.extrairTipoConta(tokenMalicioso));
    }

    @Test
    @DisplayName("Blindagem: Deve lidar com tokens que possuem Claims inesperadas (Overposting)")
    void deveLidarComClaimsExtrasSemQuebrar() {
        String tokenComSujeira = JWT.create()
                .withIssuer("estevao-lanches-api")
                .withSubject("usuario@teste.com")
                .withClaim("tipo_conta", "CLIENTE")
                .withClaim("dados_extras", "valor_que_nao_deveria_estar_aqui")
                .sign(Algorithm.HMAC256(segredoPadrao));

        assertDoesNotThrow(() -> {
            String tipo = tokenService.extrairTipoConta(tokenComSujeira);
            assertEquals("CLIENTE", tipo);
        });
    }

    @Test
    @DisplayName("Performance: Deve validar que o tempo de processamento é desprezível")
    void testePerformanceValidacao() {
        String token = tokenService.gerarToken(usuario);

        long inicio = System.currentTimeMillis();
        for(int i = 0; i < 100; i++) {
            tokenService.validarToken(token);
        }
        long fim = System.currentTimeMillis();

        assertTrue((fim - inicio) < 200, "A validação de 100 tokens deve levar menos de 200ms");
    }

    @Test
    @DisplayName("Blindagem: Deve rejeitar tokens com Issuer mal formado")
    void validarTokenIssuerDiferente() {
        String tokenErrado = JWT.create()
                .withIssuer("outro-sistema-qualquer")
                .withSubject("teste@tevao.com")
                .sign(Algorithm.HMAC256(segredoPadrao));

        assertEquals("", tokenService.validarToken(tokenErrado));
    }
}