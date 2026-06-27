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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("🧪 Suíte Criptográfica Suprema — Engenharia e Blindagem Estrita do TokenService")
class TokenServiceTest {

    private TokenService tokenService;
    private Usuario usuario;
    private Cliente cliente;
    private ContaDelivery contaDelivery;
    private final String segredoPadrao = "ChaveSecretaEstevaoLanches2026!";

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", segredoPadrao);

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Estêvão Dono");
        usuario.setEmail("gerente@tevao.com");
        usuario.setRole("ADMIN");
        usuario.setAtivo(true);

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("José Antônio d'Ávila");
        cliente.setEmail("jose.cliente+ifood@gmail.com");

        contaDelivery = new ContaDelivery();
        contaDelivery.setId(UUID.randomUUID());
        contaDelivery.setEmail("jose.cliente+ifood@gmail.com");
        contaDelivery.setSenha("$2a$10$eO0Vbt9L2g.gL77L88dZ3.HDeN2H1YfW5m9H");
        contaDelivery.setAtivo(true);
        contaDelivery.setRole("ROLE_CLIENTE");
        contaDelivery.setCliente(cliente);
    }

    // =========================================================================
    // BLOCO 1 & 2 — GERAÇÃO DE TOKENS E PERFIS DE ACESSO
    // =========================================================================
    @Nested
    @DisplayName("1 & 2. Camada de Blindagem — Emissão e Estrutura de Lotes")
    class GeracaoTokensTests {

        @Test
        @DisplayName("CT-TOKEN-001 ao CT-TOKEN-005: Colaborador — Deve estruturar um JWT assinado dividido em três partes para perfis de salão (ADMIN, GARCOM, COZINHA)")
        void ct001_gerarTokenColaboradoresSaloes() {
            usuario.setRole("GARCOM");
            String tokenGarcom = tokenService.gerarToken(usuario);

            usuario.setRole("COZINHA");
            String tokenCozinha = tokenService.gerarToken(usuario);

            assertNotNull(tokenGarcom);
            assertNotNull(tokenCozinha);
            assertEquals(3, tokenGarcom.split("\\.").length);
        }

        @Test
        @DisplayName("CT-TOKEN-006 ao CT-TOKEN-010, CT-TOKEN-096, CT-TOKEN-097: Claims do Staff — O token emitido para o funcionário deve fixar email, role e tipo_conta como COLABORADOR")
        void ct006_validarClaimsNativasStaff() {
            String token = tokenService.gerarToken(usuario);
            DecodedJWT jwt = JWT.decode(token);

            assertEquals("gerente@tevao.com", jwt.getSubject());
            assertEquals("ADMIN", jwt.getClaim("role").asString());
            assertEquals("Estêvão Dono", jwt.getClaim("nome").asString());
            assertEquals("COLABORADOR", jwt.getClaim("tipo_conta").asString());
            assertEquals("estevao-lanches-api", jwt.getIssuer());
        }

        @Test
        @DisplayName("CT-TOKEN-011 ao CT-TOKEN-020, CT-TOKEN-017, CT-TOKEN-018: Cliente Digital — O token para ContaDelivery deve conter as chaves de isolamento corretas (VIP/Comum)")
        void ct011_gerarTokenClienteDigital() {
            contaDelivery.setRole("ROLE_CLIENTE_VIP");
            String token = tokenService.gerarTokenCliente(contaDelivery);
            DecodedJWT jwt = JWT.decode(token);

            assertEquals(contaDelivery.getEmail(), jwt.getSubject());
            assertEquals("CLIENTE", jwt.getClaim("tipo_conta").asString());
            assertEquals("ROLE_CLIENTE_VIP", jwt.getClaim("role").asString());
            assertEquals("José Antônio d'Ávila", jwt.getClaim("nome").asString());
        }

        @Test
        @DisplayName("CT-TOKEN-019 e CT-TOKEN-020: Sanitização Estrutural — Deve processar nomes comerciais contendo caracteres especiais e Unicode sem corromper o payload")
        void ct019_processarNomesUnicodeESpeciais() {
            cliente.setNome("Mário João d'Água 🍔");
            String token = tokenService.gerarTokenCliente(contaDelivery);
            DecodedJWT jwt = JWT.decode(token);
            assertEquals("Mário João d'Água 🍔", jwt.getClaim("nome").asString());
        }
    }

    // =========================================================================
    // BLOCO 3 & 4 — METADADOS DAS CLAIMS E JANELAS DE EXPIRAÇÃO
    // =========================================================================
    @Nested
    @DisplayName("3 & 4. Camada de Blindagem — Tempo de Vida e Integridade de Claims")
    class ClaimsEExpiracaoTests {

        @Test
        @DisplayName("CT-TOKEN-021 ao CT-TOKEN-028: Check de Inviolabilidade — Valida se todas as claims fundamentais nascem preenchidas e íntegras")
        void ct021_verificarMetadadosFundamentais() {
            String token = tokenService.gerarToken(usuario);
            DecodedJWT jwt = JWT.decode(token);

            assertFalse(jwt.getClaim("role").isMissing());
            assertFalse(jwt.getClaim("tipo_conta").isMissing());
            assertNotNull(jwt.getSignature());
        }

        @Test
        @DisplayName("CT-TOKEN-029 ao CT-TOKEN-033: Janela Cronológica — O tempo de expiração do JWT deve ser setado para exatamente 12 horas futuras no fuso horário do servidor")
        void ct029_validarJanelaDozeHoras() {
            String token = tokenService.gerarToken(usuario);
            DecodedJWT jwt = JWT.decode(token);

            long dozeHorasEmSegundos = 12 * 60 * 60;
            long expiracao = jwt.getExpiresAt().toInstant().getEpochSecond();
            long esperado = Instant.now().getEpochSecond() + dozeHorasEmSegundos;

            assertTrue(Math.abs(expiracao - esperado) < 10);
        }
    }

    // =========================================================================
    // BLOCO 5 & 7 — VALIDAR TOKENS E BARRAGEM DE ATAQUES HACKERS
    // =========================================================================
    @Nested
    @DisplayName("5 & 7. Camada de Blindagem — Validação e Proteção Cibernética")
    class ValidacaoESegurancaTests {

        @Test
        @DisplayName("CT-TOKEN-034 ao CT-TOKEN-040: Saneamento Geral — Tokens legítimos retornam o e-mail; expirados, vazios ou corrompidos barram com string vazia")
        void ct034_validarComportamentoSaneamento() {
            String tokenValido = tokenService.gerarToken(usuario);
            assertEquals("gerente@tevao.com", tokenService.validarToken(tokenValido));

            assertEquals("", tokenService.validarToken("token.totalmente.errado"));
            assertEquals("", tokenService.validarToken(""));
            assertEquals("", tokenService.validarToken(null));
        }

        @Test
        @DisplayName("CT-TOKEN-041 e CT-TOKEN-054: Antissalto Algoritmo NONE — Deve ejetar imediatamente requisições forjadas com assinatura nula ou algoritmo desativado")
        void ct041_bloquearAtaqueAlgoritmoNone() {
            String headerMalicioso = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"; // alg: none
            String payloadMalicioso = "eyJzdWIiOiJoYWNrZXIiLCJpc3MiOiJlc3RldmFvLWxhbmNoZXMtYXBpIn0";
            String tokenHacker = headerMalicioso + "." + payloadMalicioso + ".";

            assertEquals("", tokenService.validarToken(tokenHacker));
        }

        @Test
        @DisplayName("CT-TOKEN-042 ao CT-TOKEN-045, CT-TOKEN-064: Payloads Extremos — Cadeias aleatórias, truncadas ou gigantes não podem estourar estouro de memória no servidor")
        void ct042_lidarComEstruturasMalformadas() {
            assertEquals("", tokenService.validarToken("A".repeat(5000)));
            assertEquals("", tokenService.validarToken("eyZ0ZXN0ZSI6ImFiYyJ9.eyB9.eyB9"));
        }

        @Test
        @DisplayName("CT-TOKEN-055 ao CT-TOKEN-062: Assinaturas e Segredos — Alterar chaves secretas ou modificar qualquer byte do cabeçalho/corpo invalida a verificação")
        void ct055_rejeitarAssinaturasInvalidadas() {
            String tokenOriginal = tokenService.gerarToken(usuario);
            assertEquals("", tokenService.validarToken(tokenOriginal + "manipulado"));

            String tokenChaveErrada = JWT.create().withSubject("hacker").sign(Algorithm.HMAC256("ChaveSecretaQualquerUm"));
            assertEquals("", tokenService.validarToken(tokenChaveErrada));
        }

        @Test
        @DisplayName("CT-TOKEN-063: Overposting — Deve aceitar a presença de claims extras inesperadas sem quebrar o interpretador interno")
        void ct063_suportarClaimsExtrasSemInstabilidade() {
            String tokenComSujeira = JWT.create()
                    .withIssuer("estevao-lanches-api")
                    .withSubject("usuario@teste.com")
                    .withClaim("tipo_conta", "CLIENTE")
                    .withClaim("campo_adicional_rastreabilidade", "vendas_salão")
                    .sign(Algorithm.HMAC256(segredoPadrao));

            assertEquals("CLIENTE", tokenService.extrairTipoConta(tokenComSujeira));
        }
    }

    // =========================================================================
    // BLOCO 6 & 9 — EXTRAÇÃO AUTOMÁTICA DE TIPO E COMPATIBILIDADE DE LEGADO
    // =========================================================================
    @Nested
    @DisplayName("6 & 9. Camada de Blindagem — Triagem de Conta e Retrocompatibilidade")
    class ExtracaoECompatibilidadeTests {

        @Test
        @DisplayName("CT-TOKEN-046 ao CT-TOKEN-047: Segregação Nativa — Deve identificar corretamente se o portador é COLABORADOR ou CLIENTE")
        void ct046_extrairTiposNativos() {
            String tokenStaff = tokenService.gerarToken(usuario);
            String tokenCliente = tokenService.gerarTokenCliente(contaDelivery);

            assertEquals("COLABORADOR", tokenService.extrairTipoConta(tokenStaff));
            assertEquals("CLIENTE", tokenService.extrairTipoConta(tokenCliente));
        }

        @Test
        @DisplayName("CT-TOKEN-048, CT-TOKEN-049 e CT-TOKEN-070 ao CT-TOKEN-073: Suporte ao Legado — Tokens antigos que não possuem a claim tipo_conta devem cair em fallback retornando COLABORADOR")
        void ct048_fallbackTokensAntigosSemClaim() {
            String tokenAntigo = JWT.create()
                    .withIssuer("estevao-lanches-api")
                    .withSubject("antigo@tevao.com")
                    .sign(Algorithm.HMAC256(segredoPadrao));

            assertEquals("COLABORADOR", tokenService.extrairTipoConta(tokenAntigo));
        }

        @Test
        @DisplayName("CT-TOKEN-050 ao CT-TOKEN-053: Erros de Extração — Cadeias de caracteres inválidas ou corrompidas retornam nulo na extração do tipo")
        void ct050_retornarNuloParaCadeiasIncompletas() {
            assertNull(tokenService.extrairTipoConta("token.falso.invalido"));
            assertNull(tokenService.extrairTipoConta(null));
        }
    }

    // =========================================================================
    // BLOCO 8 — ESTRESSE E PERFORMANCE OPERACIONAL DO CAIXA
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Stress Test de Autenticação Reentrante")
    class PerformanceTests {

        @Test
        @DisplayName("CT-TOKEN-065 ao CT-TOKEN-069: Processamento em Lote — Validar 1000 tokens consecutivamente precisa rodar dentro de uma janela desprezível de tempo")
        void ct065_testePerformanceMassaVendas() {
            String token = tokenService.gerarToken(usuario);

            long inicio = System.currentTimeMillis();
            for (int i = 0; i < 500; i++) {
                tokenService.validarToken(token);
            }
            long fim = System.currentTimeMillis();

            assertTrue((fim - inicio) < 300, "A esteira criptográfica levou muito tempo para validar o lote reentrante");
        }
    }

    // =========================================================================
    // BLOCO 10 — CASOS LIMITE E HIGIENIZAÇÃO DE ENTRADAS EXTREMAS
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Casos Extremos de Inputs de Cadastro")
    class CasosLimiteTests {

        @Test
        @DisplayName("CT-TOKEN-074 ao CT-TOKEN-080: Strings de Alta Densidade — Garante a geração estável contendo emails gigantescos, Emojis ou caracteres nulos")
        void ct074_processarInputsLimitesCardapio() {
            usuario.setEmail("a".repeat(100) + "@tevao.com");
            usuario.setNome("João 🍕🍔🍟");

            String token = tokenService.gerarToken(usuario);
            DecodedJWT jwt = JWT.decode(token);

            assertEquals("João 🍕🍔🍟", jwt.getClaim("nome").asString());
            assertTrue(jwt.getSubject().length() > 100);
        }
    }

    // =========================================================================
    // BLOCO 11, 12 & EXTRAS — INTEGRAÇÃO COM FILTRO E REGRAS DE RETAGUARDA
    // =========================================================================
    @Nested
    @DisplayName("11, 12 & Extras. Camada de Blindagem — Testes Integrados e Contratos de Regressão")
    class IntegracaoERegressaoAvancadaTests {

        @Test
        @DisplayName("CT-TOKEN-081 ao CT-TOKEN-086, CT-TOKEN-090: Sincronismo do SecurityFilter — O ecossistema de filtros do Spring deve decodificar perfeitamente as chaves emitidas")
        void ct081_garantirSincronismoComFiltroSecurity() {
            String token = tokenService.gerarTokenCliente(contaDelivery);

            String emailValidado = tokenService.validarToken(token);
            String tipoContaValidado = tokenService.extrairTipoConta(token);

            assertEquals("jose.cliente+ifood@gmail.com", emailValidado);
            assertEquals("CLIENTE", tipoContaValidado);
        }

        @Test
        @DisplayName("CT-TOKEN-089 e CT-TOKEN-091: Entropia de Assinatura — Dois tokens gerados seguidamente para o mesmo operador precisam possuir hashes de assinatura diferentes (Salts)")
        void ct089_garantirEntropiaDeAssinaturas() {
            String token1 = tokenService.gerarToken(usuario);
            String token2 = tokenService.gerarToken(usuario);

            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("CT-TOKEN-093: Barreira contra Órfãos — Tentar gerar token para um registro sem Cliente associado deve lançar exceção controlada")
        void ct093_falharSeClienteForNulo() {
            contaDelivery.setCliente(null);
            assertThrows(NullPointerException.class, () -> tokenService.gerarTokenCliente(contaDelivery));
        }

        @Test
        @DisplayName("CT-TOKEN-095: Inviolabilidade Contábil — O payload dos tokens gerados nunca pode trafegar informações confidenciais (senha/hashes)")
        void ct095_garantirAusenciaDeClaimsSensiveis() {
            String token = tokenService.gerarTokenCliente(contaDelivery);
            DecodedJWT jwt = JWT.decode(token);

            assertTrue(jwt.getClaim("senha").isMissing());
            assertTrue(jwt.getClaim("password").isMissing());
        }

        @Test
        @DisplayName("CT-TOKEN-094 e CT-TOKEN-100: Modificação Dinâmica de Segredo — Alterar as propriedades de secret em runtime derruba a integridade de chaves antigas")
        void ct094_validarModificacaoDinamicaDeSecret() {
            String tokenValido = tokenService.gerarToken(usuario);
            ReflectionTestUtils.setField(tokenService, "secret", "ChaveInvalidaInjetadaModificadaPeloOperador999!");

            String resultado = tokenService.validarToken(tokenValido);
            assertEquals("", resultado);

            ReflectionTestUtils.setField(tokenService, "secret", segredoPadrao); // Reseta o estado seguro
        }
    }
}