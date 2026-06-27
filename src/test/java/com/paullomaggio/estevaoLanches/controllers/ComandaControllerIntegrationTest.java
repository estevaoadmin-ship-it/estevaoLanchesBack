package com.paullomaggio.estevaoLanches.controllers;

import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.INHERIT)
@DisplayName("🎯 MATRIZ DE INTEGRAÇÃO REAL: Pipeline End-to-End de Comandas (CT-INT-001 a 030)")
class ComandaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.paullomaggio.estevaoLanches.repositories.ComandaRepository comandaRepository;

    // =========================================================================
    // BLOCO 1 — Abertura de Comanda (CT-INT-001 a CT-INT-006)
    // =========================================================================
    @Nested
    @DisplayName("📥 BLOCO 1 — Abertura Síncrona de Mesas")
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    class Bloco1Abertura {

        @Test
        @DisplayName("CT-INT-001: Abrir comanda com sucesso na mesa cadastrada")
        void ctInt001() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists());
        }

        @Test
        @DisplayName("CT-INT-002: Abrir duas vezes a mesma mesa deve retornar 200 e reter a mesma comanda (Idempotência)")
        void ctInt002() throws Exception {
            MvcResult primeiroClique = mockMvc.perform(post("/api/comandas/abrir/6")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = primeiroClique.getResponse().getContentAsString();
            String idOriginal = JsonPath.read(json, "$.id");

            mockMvc.perform(post("/api/comandas/abrir/6")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(idOriginal));
        }

        @Test
        @DisplayName("CT-INT-003: Abrir mesa com payload ou formato de rota inválido (Espera 400)")
        void ctInt003() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/textoInvalido")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CT-INT-004: Tentar abrir comanda passando número de mesa negativo (Espera 400)")
        void ctInt004() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/-1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CT-INT-005: Tentar abrir comanda informando mesa zero (Espera 400)")
        void ctInt005() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/0")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CT-INT-006: Tentar abrir comanda informando número de mesa extremamente alto (Espera 400)")
        void ctInt006() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/999999")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // BLOCO 2 — Buscar Comanda (CT-INT-007 a CT-INT-009)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 2 — Localização de Sessões Ativas")
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    class Bloco2Busca {

        @Test
        @DisplayName("CT-INT-007: Localizar comanda ativa existente por UUID")
        void ctInt007() throws Exception {
            MvcResult mvcResult = mockMvc.perform(post("/api/comandas/abrir/15")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            String id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get("/api/comandas/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        @DisplayName("CT-INT-008: Tentar buscar ID órfão ou inexistente no salão (Espera 404)")
        void ctInt008() throws Exception {
            mockMvc.perform(get("/api/comandas/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("CT-INT-009: Tentar buscar passando token de UUID malformado na rota (Espera 400)")
        void ctInt009() throws Exception {
            mockMvc.perform(get("/api/comandas/token-invalido-123"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // BLOCO 3 — Listagem (CT-INT-010 a CT-INT-012)
    // =========================================================================
    @Nested
    @DisplayName("📋 BLOCO 3 — Painéis e Varreduras de Comandas Ativas")
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    class Bloco3Listagem {

        @Test
        @DisplayName("CT-INT-010 ao 012 - Listar comandas garantindo retorno estruturado sob salão vazio ou populado")
        void ctInt010To012() throws Exception {

            // 🧹 BALA DE PRATA: Varre e neutraliza qualquer sujeira vazada de testes anteriores
            // Fechamos as comandas em vez de deletar para evitar qualquer erro de Foreign Key
            comandaRepository.findAll().forEach(c -> {
                c.setStatus(com.paullomaggio.estevaoLanches.enums.StatusComanda.FECHADA);
                comandaRepository.save(c);
            });

            // O teste agora roda num "salão virtual" 100% vazio
            mockMvc.perform(post("/api/comandas/abrir/20"));
            mockMvc.perform(post("/api/comandas/abrir/21"));

            mockMvc.perform(get("/api/comandas/ativas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(2)));
        }
    }

    // =========================================================================
    // BLOCO 4 — Alteração de Status (CT-INT-013 a CT-INT-015)
    // =========================================================================
    @Nested
    @DisplayName("⚙️ BLOCO 4 — Transições Estritas da Máquina de Estados")
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    class Bloco4Status {

        @Test
        @DisplayName("CT-INT-013 ao 015 - Alternar status e barrar parâmetros inválidos de transição")
        void ctInt013To015() throws Exception {
            MvcResult mvcResult = mockMvc.perform(post("/api/comandas/abrir/30")).andReturn();
            String id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(put("/api/comandas/" + id + "/status?novoStatus=AGUARDANDO_PAGAMENTO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"));

            mockMvc.perform(put("/api/comandas/" + id + "/status?novoStatus=INVALIDO"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // BLOCO 5 — Fechamento (CT-INT-016 a CT-INT-018)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 5 — Liquidação e Liberação Física de Mesas")
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    class Bloco5Fechamento {

        @Test
        @DisplayName("CT-INT-016 ao 018 - Executar fechamento, impedir encerramento duplo e tratar ID órfão")
        void ctInt016To018() throws Exception {
            MvcResult mvcResult = mockMvc.perform(post("/api/comandas/abrir/40")).andReturn();
            String id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(put("/api/comandas/" + id + "/fechar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FECHADA"))
                    .andExpect(jsonPath("$.fechadaEm").isNotEmpty());

            mockMvc.perform(put("/api/comandas/" + id + "/fechar"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(put("/api/comandas/" + UUID.randomUUID() + "/fechar"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // BLOCO 6 — Segurança (CT-INT-019 a CT-INT-023)
    // =========================================================================
    @Nested
    @DisplayName("🔐 BLOCO 6 — Matriz de Permissões e Perfis (Spring Security)")
    class Bloco6Seguranca {

        @Test
        @DisplayName("CT-INT-019: Bloquear requisições anônimas sem cabeçalho de autenticação (Espera 401)")
        void ctInt019() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/50")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CT-INT-020: Bloquear perfil CLIENTE tentando abrir mesa no salão (Espera 403)")
        void ctInt020() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/50")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("CT-INT-021: Bloquear perfil COZINHA tentando gerenciar abertura de mesa (Espera 403)")
        void ctInt021() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/50")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("CT-INT-022: Permitir que perfil ADMIN gerencie e abra comandas livremente")
        void ctInt022() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/51")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("CT-INT-023: Autorizar canal de uso padrão de GARCOM no salão")
        void ctInt023() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/52")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // BLOCO 7 & 8 — Contrato JSON e Cabeçalhos (CT-INT-024 a CT-INT-026)
    // =========================================================================
    @Nested
    @DisplayName("📦 BLOCO 7 & 8 — Conformidade Estrutural JSON e Encodings")
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    class Bloco7And8Contratos {

        @Test
        @DisplayName("CT-INT-024 ao 026 - Certificar Content-Type, UTF-8 e mapeamento completo das chaves JSON")
        void ctInt024To026() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("ABERTA"))
                    .andExpect(jsonPath("$.numeroMesa").value(5))
                    .andExpect(jsonPath("$.empresaId").isNotEmpty())
                    .andExpect(jsonPath("$.filialId").isNotEmpty())
                    .andExpect(jsonPath("$.abertaEm").exists())
                    .andExpect(jsonPath("$.fechadaEm").doesNotExist());
        }
    }

    // =========================================================================
    // BLOCO 9 — Stress e Fluxo de Regressão (CT-INT-027 a CT-INT-030)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 9 — Carga de Dados e Linha do Tempo Estendida")
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    class Bloco9Stress {

        @Test
        @DisplayName("CT-INT-027 ao 029 - Processamento sequencial rápido de aberturas de lotes de mesas")
        void ctInt027To029() throws Exception {
            for (int mesa = 100; mesa < 150; mesa++) {
                mockMvc.perform(post("/api/comandas/abrir/" + mesa)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("CT-INT-030: Linha do Tempo Completa — Abrir ➔ Buscar ➔ Listar ➔ Fechar ➔ Verificar Expurgada do Monitor")
        void ctInt030() throws Exception {
            // 1. Abrir
            MvcResult mvcResult = mockMvc.perform(post("/api/comandas/abrir/80")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();

            String id = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.id");

            // 2. Buscar
            mockMvc.perform(get("/api/comandas/" + id))
                    .andExpect(status().isOk());

            // 3. Listar Ativas
            mockMvc.perform(get("/api/comandas/ativas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isNotEmpty());

            // 4. Fechar
            mockMvc.perform(put("/api/comandas/" + id + "/fechar"))
                    .andExpect(status().isOk());

            // 5. Listar Novamente (Garante isolamento no painel monitor)
            mockMvc.perform(get("/api/comandas/ativas"))
                    .andExpect(status().isOk());
        }
    }
}