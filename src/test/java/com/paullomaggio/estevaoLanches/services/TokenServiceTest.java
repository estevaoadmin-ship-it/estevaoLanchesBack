package com.paullomaggio.estevaoLanches.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenService - JWT de colaboradores e clientes delivery")
class TokenServiceTest {

    private TokenService tokenService;
    private Usuario colaborador;
    private Cliente cliente;
    private ContaDelivery contaDelivery;
    private final String segredoPadrao = "ChaveSecretaEstevaoLanches2026!";

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", segredoPadrao);

        colaborador = new Usuario();
        colaborador.setId(UUID.randomUUID());
        colaborador.setNome("Estevao Dono");
        colaborador.setEmail("gerente@tevao.com");
        colaborador.setSenha("$2a$10$hashColaborador");
        colaborador.setRole("ADMIN");
        colaborador.setAtivo(true);

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Jose Antonio Delivery");
        cliente.setEmail("jose.cliente+delivery@gmail.com");

        contaDelivery = new ContaDelivery();
        contaDelivery.setId(UUID.randomUUID());
        contaDelivery.setEmail("jose.cliente+delivery@gmail.com");
        contaDelivery.setSenha("$2a$10$hashContaDelivery");
        contaDelivery.setAtivo(true);
        contaDelivery.setRole("ROLE_CLIENTE");
        contaDelivery.setCliente(cliente);
    }

    @Nested
    @DisplayName("1 e 2. Emissao e estrutura")
    class GeracaoTokensTests {

        @Test
        @DisplayName("CT-TOKEN-001 ao CT-TOKEN-005 - Gera JWT valido para perfis de colaboradores")
        void ct001_gerarTokenColaboradoresSaloes() {
            colaborador.setRole("GARCOM");
            String tokenGarcom = tokenService.gerarToken(colaborador);

            colaborador.setRole("COZINHA");
            String tokenCozinha = tokenService.gerarToken(colaborador);

            assertNotNull(tokenGarcom);
            assertNotNull(tokenCozinha);
            assertEquals(3, tokenGarcom.split("\\.").length);
            assertEquals("gerente@tevao.com", tokenService.validarToken(tokenCozinha));
        }

        @Test
        @DisplayName("CT-TOKEN-006 ao CT-TOKEN-010, CT-TOKEN-096, CT-TOKEN-097 - Claims de colaborador")
        void ct006_validarClaimsNativasStaff() {
            String token = tokenService.gerarToken(colaborador);
            DecodedJWT jwt = JWT.decode(token);

            assertEquals("gerente@tevao.com", jwt.getSubject());
            assertEquals("ADMIN", jwt.getClaim("role").asString());
            assertEquals("Estevao Dono", jwt.getClaim("nome").asString());
            assertEquals("COLABORADOR", jwt.getClaim("tipo_conta").asString());
            assertEquals("estevao-lanches-api", jwt.getIssuer());
            assertFalse(jwt.getClaim("role").asString().startsWith("ROLE_"));
        }

        @Test
        @DisplayName("CT-TOKEN-011 ao CT-TOKEN-020 - Claims de ContaDelivery")
        void ct011_gerarTokenClienteDigital() {
            String token = tokenService.gerarTokenCliente(contaDelivery);
            DecodedJWT jwt = JWT.decode(token);

            assertEquals(contaDelivery.getEmail(), jwt.getSubject());
            assertEquals("CLIENTE", jwt.getClaim("tipo_conta").asString());
            assertEquals("ROLE_CLIENTE", jwt.getClaim("role").asString());
            assertEquals("Jose Antonio Delivery", jwt.getClaim("nome").asString());
        }

        @Test
        @DisplayName("CT-TOKEN-019 e CT-TOKEN-020 - Preserva nomes comerciais com acentos e simbolos")
        void ct019_processarNomesUnicodeESpeciais() {
            cliente.setNome("Mario Joao d'Agua #42");

            String token = tokenService.gerarTokenCliente(contaDelivery);
            DecodedJWT jwt = JWT.decode(token);

            assertEquals("Mario Joao d'Agua #42", jwt.getClaim("nome").asString());
        }
    }

    @Nested
    @DisplayName("3 e 4. Claims e expiracao")
    class ClaimsEExpiracaoTests {

        @Test
        @DisplayName("CT-TOKEN-021 ao CT-TOKEN-028 - Claims fundamentais nascem preenchidas")
        void ct021_verificarMetadadosFundamentais() {
            String token = tokenService.gerarToken(colaborador);
            DecodedJWT jwt = JWT.decode(token);

            assertFalse(jwt.getClaim("role").isMissing());
            assertFalse(jwt.getClaim("nome").isMissing());
            assertFalse(jwt.getClaim("tipo_conta").isMissing());
            assertNotNull(jwt.getExpiresAt());
            assertNotNull(jwt.getSignature());
        }

        @Test
        @DisplayName("CT-TOKEN-029 ao CT-TOKEN-033 - Expiracao fica em aproximadamente 12 horas")
        void ct029_validarJanelaDozeHoras() {
            String token = tokenService.gerarToken(colaborador);
            DecodedJWT jwt = JWT.decode(token);

            long expiracao = jwt.getExpiresAt().toInstant().getEpochSecond();
            long esperado = Instant.now().plusSeconds(12 * 60 * 60).getEpochSecond();

            assertTrue(Math.abs(expiracao - esperado) < 10);
        }
    }

    @Nested
    @DisplayName("5 e 7. Validacao e seguranca")
    class ValidacaoESegurancaTests {

        @Test
        @DisplayName("CT-TOKEN-034 ao CT-TOKEN-040 - Token valido retorna subject e token invalido retorna string vazia")
        void ct034_validarComportamentoSaneamento() {
            String tokenValido = tokenService.gerarToken(colaborador);
            String tokenExpirado = JWT.create()
                    .withIssuer("estevao-lanches-api")
                    .withSubject("expirado@tevao.com")
                    .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
                    .sign(Algorithm.HMAC256(segredoPadrao));

            assertEquals("gerente@tevao.com", tokenService.validarToken(tokenValido));
            assertEquals("", tokenService.validarToken(tokenExpirado));
            assertEquals("", tokenService.validarToken("token.totalmente.errado"));
            assertEquals("", tokenService.validarToken(""));
            assertEquals("", tokenService.validarToken(null));
        }

        @Test
        @DisplayName("CT-TOKEN-041 e CT-TOKEN-054 - Bloqueia algoritmo none")
        void ct041_bloquearAtaqueAlgoritmoNone() {
            String headerMalicioso = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0";
            String payloadMalicioso = "eyJzdWIiOiJoYWNrZXIiLCJpc3MiOiJlc3RldmFvLWxhbmNoZXMtYXBpIn0";
            String tokenHacker = headerMalicioso + "." + payloadMalicioso + ".";

            assertEquals("", tokenService.validarToken(tokenHacker));
        }

        @Test
        @DisplayName("CT-TOKEN-042 ao CT-TOKEN-045, CT-TOKEN-064 - Rejeita estruturas malformadas sem propagar excecao")
        void ct042_lidarComEstruturasMalformadas() {
            assertEquals("", tokenService.validarToken("A".repeat(5000)));
            assertEquals("", tokenService.validarToken("eyZ0ZXN0ZSI6ImFiYyJ9.eyB9.eyB9"));
        }

        @Test
        @DisplayName("CT-TOKEN-055 ao CT-TOKEN-062 - Rejeita assinatura manipulada ou segredo incorreto")
        void ct055_rejeitarAssinaturasInvalidadas() {
            String tokenOriginal = tokenService.gerarToken(colaborador);
            String tokenChaveErrada = JWT.create()
                    .withIssuer("estevao-lanches-api")
                    .withSubject("hacker@tevao.com")
                    .sign(Algorithm.HMAC256("ChaveSecretaQualquerUm"));

            assertEquals("", tokenService.validarToken(tokenOriginal + "manipulado"));
            assertEquals("", tokenService.validarToken(tokenChaveErrada));
        }

        @Test
        @DisplayName("CT-TOKEN-063 - Claims extras nao quebram a extracao do tipo de conta")
        void ct063_suportarClaimsExtrasSemInstabilidade() {
            String tokenComClaimExtra = JWT.create()
                    .withIssuer("estevao-lanches-api")
                    .withSubject("cliente@teste.com")
                    .withClaim("tipo_conta", "CLIENTE")
                    .withClaim("campo_adicional_rastreabilidade", "delivery-app")
                    .sign(Algorithm.HMAC256(segredoPadrao));

            assertEquals("CLIENTE", tokenService.extrairTipoConta(tokenComClaimExtra));
        }
    }

    @Nested
    @DisplayName("6 e 9. Tipo de conta e legado")
    class ExtracaoECompatibilidadeTests {

        @Test
        @DisplayName("CT-TOKEN-046 ao CT-TOKEN-047 - Distingue colaborador e cliente")
        void ct046_extrairTiposNativos() {
            String tokenStaff = tokenService.gerarToken(colaborador);
            String tokenCliente = tokenService.gerarTokenCliente(contaDelivery);

            assertEquals("COLABORADOR", tokenService.extrairTipoConta(tokenStaff));
            assertEquals("CLIENTE", tokenService.extrairTipoConta(tokenCliente));
        }

        @Test
        @DisplayName("CT-TOKEN-048, CT-TOKEN-049 e CT-TOKEN-070 ao CT-TOKEN-073 - Token legado sem tipo_conta cai como colaborador")
        void ct048_fallbackTokensAntigosSemClaim() {
            String tokenAntigo = JWT.create()
                    .withIssuer("estevao-lanches-api")
                    .withSubject("antigo@tevao.com")
                    .sign(Algorithm.HMAC256(segredoPadrao));

            assertEquals("COLABORADOR", tokenService.extrairTipoConta(tokenAntigo));
        }

        @Test
        @DisplayName("CT-TOKEN-050 ao CT-TOKEN-053 - Token invalido retorna nulo na extracao de tipo")
        void ct050_retornarNuloParaCadeiasIncompletas() {
            assertNull(tokenService.extrairTipoConta("token.falso.invalido"));
            assertNull(tokenService.extrairTipoConta(null));
        }
    }

    @Nested
    @DisplayName("8. Stress de validacao")
    class PerformanceTests {

        @Test
        @DisplayName("CT-TOKEN-065 ao CT-TOKEN-069 - Valida lote de tokens sem degradacao evidente")
        void ct065_testePerformanceMassaVendas() {
            String token = tokenService.gerarToken(colaborador);

            long inicio = System.currentTimeMillis();
            for (int i = 0; i < 500; i++) {
                assertEquals("gerente@tevao.com", tokenService.validarToken(token));
            }
            long fim = System.currentTimeMillis();

            assertTrue((fim - inicio) < 1000);
        }
    }

    @Nested
    @DisplayName("10. Casos limite")
    class CasosLimiteTests {

        @Test
        @DisplayName("CT-TOKEN-074 ao CT-TOKEN-080 - Suporta e-mail e nome grandes no payload")
        void ct074_processarInputsLimitesCardapio() {
            colaborador.setEmail("a".repeat(100) + "@tevao.com");
            colaborador.setNome("Joao Operador Caixa 01");

            String token = tokenService.gerarToken(colaborador);
            DecodedJWT jwt = JWT.decode(token);

            assertEquals("Joao Operador Caixa 01", jwt.getClaim("nome").asString());
            assertTrue(jwt.getSubject().length() > 100);
        }
    }

    @Nested
    @DisplayName("11, 12 e extras. Integracao e regressao")
    class IntegracaoERegressaoAvancadaTests {

        @Test
        @DisplayName("CT-TOKEN-081 ao CT-TOKEN-086, CT-TOKEN-090 - SecurityFilter consegue usar subject e tipo emitidos")
        void ct081_garantirSincronismoComFiltroSecurity() {
            String token = tokenService.gerarTokenCliente(contaDelivery);

            String emailValidado = tokenService.validarToken(token);
            String tipoContaValidado = tokenService.extrairTipoConta(token);

            assertEquals("jose.cliente+delivery@gmail.com", emailValidado);
            assertEquals("CLIENTE", tipoContaValidado);
        }

        @Test
        @DisplayName("CT-TOKEN-089 e CT-TOKEN-091 - Tokens consecutivos preservam identidade e assinatura valida")
        void ct089_garantirContratosDeTokensConsecutivos() {
            String token1 = tokenService.gerarToken(colaborador);
            String token2 = tokenService.gerarToken(colaborador);

            assertEquals("gerente@tevao.com", tokenService.validarToken(token1));
            assertEquals("gerente@tevao.com", tokenService.validarToken(token2));
            assertEquals("COLABORADOR", tokenService.extrairTipoConta(token1));
            assertEquals("COLABORADOR", tokenService.extrairTipoConta(token2));
        }

        @Test
        @DisplayName("CT-TOKEN-093 - ContaDelivery sem cliente associado falha ao gerar token")
        void ct093_falharSeClienteForNulo() {
            contaDelivery.setCliente(null);

            assertThrows(NullPointerException.class, () -> tokenService.gerarTokenCliente(contaDelivery));
        }

        @Test
        @DisplayName("CT-TOKEN-095 - Payload nao trafega senha nem hash")
        void ct095_garantirAusenciaDeClaimsSensiveis() {
            String token = tokenService.gerarTokenCliente(contaDelivery);
            DecodedJWT jwt = JWT.decode(token);

            assertTrue(jwt.getClaim("senha").isMissing());
            assertTrue(jwt.getClaim("password").isMissing());
            assertTrue(jwt.getClaim("hash").isMissing());
        }

        @Test
        @DisplayName("CT-TOKEN-094 e CT-TOKEN-100 - Mudanca de segredo invalida token antigo")
        void ct094_validarModificacaoDinamicaDeSecret() {
            String tokenValido = tokenService.gerarToken(colaborador);
            ReflectionTestUtils.setField(tokenService, "secret", "ChaveInvalidaInjetadaModificadaPeloOperador999!");

            assertEquals("", tokenService.validarToken(tokenValido));

            ReflectionTestUtils.setField(tokenService, "secret", segredoPadrao);
        }

        @Test
        @DisplayName("CT-TOKEN-098 e CT-TOKEN-099 - Token sem issuer nao autentica nem extrai tipo")
        void ct098_rejeitarTokenSemIssuer() {
            String tokenSemIssuer = JWT.create()
                    .withSubject("semissuer@tevao.com")
                    .withClaim("tipo_conta", "COLABORADOR")
                    .sign(Algorithm.HMAC256(segredoPadrao));

            assertEquals("", tokenService.validarToken(tokenSemIssuer));
            assertNull(tokenService.extrairTipoConta(tokenSemIssuer));
        }
    }
}
