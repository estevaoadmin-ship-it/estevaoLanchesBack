package com.paullomaggio.estevaoLanches.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("🎯 SUÍTE 1: Controle de Acesso e Autorização por Perfis (Roles)")
class SecurityAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // LEVEL 1 — ROTAS PÚBLICAS
    // =========================================================================
    @Nested
    @DisplayName("🌐 LEVEL 1 — Rotas Públicas (Acesso Livre)")
    class Level1RotasPublicas {

        @Test
        @DisplayName("🛡️ SEC001 - POST /api/auth/login deve responder com 200 ou 401, NUNCA 403")
        void sec001() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"invalido@estevaolanches.com\",\"senha\":\"123\"}"))
                    .andExpect(status().isUnauthorized()); // 401 prova que passou pelo filtro de segurança!
        }

        @Test
        @DisplayName("🛡️ SEC002 - POST /api/auth/registrar deve estar totalmente livre para o público")
        void sec002() throws Exception {
            mockMvc.perform(post("/api/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest()); // Retorna 400 devido ao @Valid, mas NUNCA 401 ou 403.
        }

        @Test
        @DisplayName("🛡️ SEC003 - POST /api/auth/login/cliente deve permitir tentativa de login de clientes")
        void sec003() throws Exception {
            mockMvc.perform(post("/api/auth/login/cliente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"cliente@comilao.com\",\"senha\":\"errada\"}"))
                    .andExpect(status().isUnauthorized()); // 401 indica que bateu na regra de negócio, livre de 403.
        }

        @Test
        @DisplayName("🛡️ SEC004 - POST /api/auth/cliente/google OAuth deve estar mapeado como endpoint público")
        void sec004() throws Exception {
            mockMvc.perform(post("/api/auth/cliente/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest()); // Passa pela segurança e barra na validação ou ausência de dados.
        }

        @Test
        @DisplayName("🛡️ SEC005 - POST /api/auth/refresh (Caso exista rota pública ou simulada de atualização)")
        void sec005() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()); // Não retorna 403, provando que não foi barrado pelo filtro básico.
        }
    }

    // =========================================================================
    // LEVEL 2 — SEM TOKEN
    // =========================================================================
    @Nested
    @DisplayName("🚫 LEVEL 2 — Requisições Sem Token / Anônimas")
    class Level2SemToken {

        @Test
        @DisplayName("🛡️ SEC006 - GET /api/cardapio sem token deve ser rejeitado com 401")
        void sec006() throws Exception {
            mockMvc.perform(get("/api/cardapio"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("🛡️ SEC007 - GET /api/pedidos sem token deve ser rejeitado com 401")
        void sec007() throws Exception {
            mockMvc.perform(get("/api/pedidos"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("🛡️ SEC008 - GET /api/caixas sem token deve ser rejeitado com 401")
        void sec008() throws Exception {
            mockMvc.perform(get("/api/caixas"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("🛡️ SEC009 - GET /api/relatorios sem token deve ser rejeitado com 401")
        void sec009() throws Exception {
            mockMvc.perform(get("/api/relatorios"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("🛡️ SEC010 - POST /api/pedidos/checkout (pagamento) sem token deve ser rejeitado com 401")
        void sec010() throws Exception {
            mockMvc.perform(post("/api/pedidos/checkout"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // LEVEL 3 — CLIENTE DELIVERY
    // =========================================================================
    @Nested
    @DisplayName("🛵 LEVEL 3 — Perfil: CLIENTE DELIVERY")
    class Level3ClienteDelivery {

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC011 - Cliente deve conseguir listar produtos do cardápio")
        void sec011() throws Exception {
            mockMvc.perform(get("/api/cardapio"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC012 - Cliente deve conseguir submeter e finalizar um pedido")
        void sec012() throws Exception {
            mockMvc.perform(post("/api/pedidos"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC013 - Cliente deve conseguir visualizar seu histórico de pedidos próprios")
        void sec013() throws Exception {
            mockMvc.perform(get("/api/pedidos/proprios"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC014 - Cliente tentando acessar pedidos de outro cliente deve receber 403")
        void sec014() throws Exception {
            mockMvc.perform(get("/api/pedidos/outro-cliente-id"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC015 - Cliente tentando acessar fluxo de Caixa deve receber 403")
        void sec015() throws Exception {
            mockMvc.perform(get("/api/caixas"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC016 - Cliente tentando acessar Relatórios Gerenciais deve receber 403")
        void sec016() throws Exception {
            mockMvc.perform(get("/api/relatorios/faturamento"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC017 - Cliente tentando acessar a listagem de Usuários internos deve receber 403")
        void sec017() throws Exception {
            mockMvc.perform(get("/api/usuarios"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC018 - Cliente de Delivery tentando gerenciar Mesas físicas deve receber 403")
        void sec018() throws Exception {
            mockMvc.perform(get("/api/comandas/mesas"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC019 - Cliente de Delivery tentando ler Contas do salão deve receber 403")
        void sec019() throws Exception {
            mockMvc.perform(get("/api/comandas/contas"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("🛡️ SEC020 - Cliente de Delivery tentando manipular Comandas físicas deve receber 403")
        void sec020() throws Exception {
            mockMvc.perform(get("/api/comandas"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // LEVEL 4 — GARÇOM
    // =========================================================================
    @Nested
    @DisplayName("🤵 LEVEL 4 — Perfil: GARÇOM")
    class Level4Garcom {

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC021 - Garçom deve ter permissão para interagir com a esteira de pedidos")
        void sec021() throws Exception {
            mockMvc.perform(get("/api/pedidos"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC022 - Garçom deve conseguir gerenciar as Mesas ativas")
        void sec022() throws Exception {
            mockMvc.perform(get("/api/comandas"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC023 - Garçom deve conseguir visualizar resumos de Conta para fechamento preliminar")
        void sec023() throws Exception {
            mockMvc.perform(get("/api/caixas/resumo"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC024 - Garçom pode enviar checkout para processar pagamento de mesa")
        void sec024() throws Exception {
            mockMvc.perform(post("/api/pedidos/checkout"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC025 - Garçom tentando listar ou criar Usuários deve receber 403")
        void sec025() throws Exception {
            mockMvc.perform(get("/api/usuarios"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC026 - Garçom tentando acessar Relatórios Financeiros consolidados deve receber 403")
        void sec026() throws Exception {
            mockMvc.perform(get("/api/relatorios/faturamento"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC027 - Garçom tentando cadastrar um Administrador deve receber 403")
        void sec027() throws Exception {
            mockMvc.perform(post("/api/usuarios"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("🛡️ SEC028 - Garçom tentando excluir ou inativar usuários deve receber 403")
        void sec028() throws Exception {
            mockMvc.perform(delete("/api/usuarios/d86b7721-729d-4e92-bc91-5da5fa774b99"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // LEVEL 5 — COZINHA
    // =========================================================================
    @Nested
    @DisplayName("🍳 LEVEL 5 — Perfil: COZINHA")
    class Level5Cozinha {

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("🛡️ SEC029 - Cozinha deve ter acesso operacional à fila de pedidos e impressão")
        void sec029() throws Exception {
            mockMvc.perform(get("/api/pedidos"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("🛡️ SEC030 - Cozinha deve conseguir alterar o status de produção de um pedido")
        void sec030() throws Exception {
            mockMvc.perform(put("/api/pedidos/alterar-status"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("🛡️ SEC031 - Cozinha não pode realizar ou processar pagamentos (403)")
        void sec031() throws Exception {
            mockMvc.perform(post("/api/pedidos/checkout"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("🛡️ SEC032 - Cozinha não pode gerenciar dados do Caixa interno (403)")
        void sec032() throws Exception {
            mockMvc.perform(get("/api/caixas"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("🛡️ SEC033 - Cozinha não pode visualizar ou alterar Usuários (403)")
        void sec033() throws Exception {
            mockMvc.perform(get("/api/usuarios"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("🛡️ SEC034 - Cozinha não pode abrir Relatórios administrativos (403)")
        void sec034() throws Exception {
            mockMvc.perform(get("/api/relatorios"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // LEVEL 6 — ADMINISTRADOR
    // =========================================================================
    @Nested
    @DisplayName("👑 LEVEL 6 — Perfil: ADMINISTRADOR")
    class Level6Administrador {

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("🛡️ SEC035 - Administrador possui passe livre e pode ler relatórios")
        void sec035() throws Exception {
            mockMvc.perform(get("/api/relatorios"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("🛡️ SEC036 - Administrador pode acessar o endpoint de cadastrar colaboradores")
        void sec036() throws Exception {
            mockMvc.perform(post("/api/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest()); // Passou da barreira 403, travou apenas na validação do DTO
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("🛡️ SEC037 - Administrador pode acessar o endpoint de exclusão/inativação")
        void sec037() throws Exception {
            mockMvc.perform(delete("/api/usuarios/d86b7721-729d-4e92-bc91-5da5fa774b99"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("🛡️ SEC038 - Administrador pode executar o fechamento definitivo de caixas")
        void sec038() throws Exception {
            mockMvc.perform(post("/api/caixas/fechar"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("🛡️ SEC039 - Administrador tem acesso à rota de Dashboards analíticos")
        void sec039() throws Exception {
            mockMvc.perform(get("/api/relatorios/dashboard"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("🛡️ SEC040 - Administrador lê e manipula toda a árvore de relatórios")
        void sec040() throws Exception {
            mockMvc.perform(get("/api/relatorios/faturamento"))
                    .andExpect(status().isOk());
        }
    }
}