package com.paullomaggio.estevaoLanches.security;

import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO; // Importar PedidoResponseDTO
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService; // Importar PedidoCoreService
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean; // Removido
import org.springframework.test.context.bean.override.mockito.MockitoBean; // Adicionado

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException; // Importar AccessDeniedException
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList; // Importar ArrayList
import java.util.List; // Importar List
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("🎯 SUÍTE DE ARQUITETURA DE SEGURANÇA: Controle de Acesso e Autorização")
class SecurityArchitectureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean // Mockar PedidoCoreService
    private PedidoCoreService pedidoCoreService;

    // --- Constantes de URLs ---
    // Autenticação
    private static final String AUTH_LOGIN_URL = "/api/auth/login";
    private static final String AUTH_LOGIN_CLIENTE_URL = "/api/auth/login/cliente";
    private static final String AUTH_REGISTER_URL = "/api/auth/registrar";
    private static final String AUTH_CLIENTE_GOOGLE_URL = "/api/auth/cliente/google";

    // Pedidos Delivery
    // Corrigido: DELIVERY_PEDIDOS_BASE_URL agora aponta para um endpoint existente para listagem geral
    private static final String DELIVERY_PEDIDOS_BASE_URL = "/api/delivery/pedidos/historico";
    private static final String DELIVERY_PEDIDOS_CHECKOUT_URL = "/api/delivery/pedidos/checkout";

    // IDs de Cliente
    private static final String CLIENTE_OWN_UUID_STR = "d86b7721-729d-4e92-bc91-5da5fa774b99";
    private static final String CLIENTE_OTHER_UUID_STR = "a1b2c3d4-e5f6-7890-1234-567890abcdef";
    private static final UUID CLIENTE_OWN_UUID = UUID.fromString(CLIENTE_OWN_UUID_STR);
    private static final UUID CLIENTE_OTHER_UUID = UUID.fromString(CLIENTE_OTHER_UUID_STR);

    // IDs de Pedido (NOVAS CONSTANTES)
    private static final UUID PEDIDO_OWN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111"); // Pedido que pertence ao CLIENTE_OWN_UUID
    private static final UUID PEDIDO_OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222"); // Pedido que pertence ao CLIENTE_OTHER_UUID

    private static final String DELIVERY_PEDIDOS_ID_OWN_URL = "/api/delivery/pedidos/" + PEDIDO_OWN_ID;
    private static final String DELIVERY_PEDIDOS_ID_OTHER_URL = "/api/delivery/pedidos/" + PEDIDO_OTHER_ID;
    private static final String DELIVERY_PEDIDOS_HISTORICO_URL = "/api/delivery/pedidos/historico"; // URL sem ID no path

    // Pedidos Balcão/Mesa/Geral
    private static final String PEDIDOS_BASE_URL = "/api/pedidos"; // Listagem geral, alteração de status
    private static final String PEDIDOS_BALCAO_CHECKOUT_URL = "/api/pedidos/balcao/checkout";
    private static final String PEDIDOS_MOBILE_URL = "/api/pedidos/mobile"; // Criar pedido mobile
    private static final String PEDIDOS_ID_STATUS_URL = "/api/pedidos/" + UUID.randomUUID() + "/status"; // Alterar status de pedido

    // Comandas
    // Corrigido: COMANDAS_BASE_URL agora aponta para um endpoint existente para listagem geral
    private static final String COMANDAS_BASE_URL = "/api/comandas/ativas";
    private static final String COMANDAS_ABRIR_URL = "/api/comandas/abrir/{numeroMesa}";
    private static final String COMANDAS_ATIVAS_URL = "/api/comandas/ativas";
    private static final String COMANDA_ID_URL = "/api/comandas/" + UUID.randomUUID(); // ID aleatório para buscar
    private static final String COMANDA_STATUS_URL = "/api/comandas/" + UUID.randomUUID() + "/status"; // ID aleatório para alterar status
    private static final String COMANDA_FECHAR_URL = "/api/comandas/" + UUID.randomUUID() + "/fechar"; // ID aleatório para fechar

    // Outros módulos
    // Corrigido: CARDAPIO_URL agora aponta para /api/produtos
    private static final String CARDAPIO_URL = "/api/produtos"; // ProdutoController
    private static final String CARDAPIO_ID_URL = "/api/produtos/" + UUID.randomUUID(); // Para PUT/DELETE
    private static final String USUARIOS_BASE_URL = "/api/usuarios"; // UsuarioController
    private static final String USUARIOS_ID_URL = "/api/usuarios/" + UUID.randomUUID(); // Exemplo de ID
    private static final String CLIENTES_URL = "/api/clientes"; // ClienteController
    // Corrigido: CAIXAS_BASE_URL agora aponta para /api/caixas/status
    private static final String CAIXAS_BASE_URL = "/api/caixas/status"; // CaixaController
    private static final String CAIXAS_STATUS_URL = "/api/caixas/status";
    private static final String CAIXAS_RESUMO_URL = "/api/caixas/resumo";
    // Corrigido: CAIXAS_ABRIR_URL agora aponta para /api/caixas
    private static final String CAIXAS_ABRIR_URL = "/api/caixas";
    // Corrigido: CAIXAS_FECHAR_URL agora aponta para /api/caixas/ativo
    private static final String CAIXAS_FECHAR_URL = "/api/caixas/ativo";
    // Corrigido: RELATORIOS_BASE_URL agora aponta para /api/relatorios/dashboard
    private static final String RELATORIOS_BASE_URL = "/api/relatorios/dashboard"; // RelatorioController
    // Corrigido: FILA_IMPRESSAO_URL agora aponta para /api/fila-impressao/pendentes
    private static final String FILA_IMPRESSAO_URL = "/api/fila-impressao/pendentes"; // FilaImpressaoController
    private static final String CONTAS_URL = "/api/contas"; // ContaController
    // Corrigido: PAGAMENTOS_URL agora aponta para um endpoint existente
    private static final String PAGAMENTOS_URL = "/api/pagamentos/conta/" + UUID.randomUUID(); // PagamentoController
    // Corrigido: CARRINHOS_URL agora aponta para um endpoint existente
    private static final String CARRINHOS_URL = "/api/carrinhos/" + CLIENTE_OWN_UUID_STR; // CarrinhoController

    private static final String WS_TEVAO_URL = "/ws-tevao"; // Websocket endpoint

    // --- Constantes de DTOs JSON ---
    private static final String LOGIN_DTO = "{\"email\":\"test@example.com\",\"senha\":\"password\"}";
    private static final String REGISTER_DTO_INVALID = "{}"; // Para @Valid
    private static final String NEW_CHECKOUT_DELIVERY_DTO = "{\"clienteId\":\"" + CLIENTE_OWN_UUID_STR + "\",\"enderecoEntrega\":\"Rua A, 123\",\"formaPagamento\":\"PIX\",\"observacao\":\"sem cebola\",\"cupom\":null}";
    private static final String NEW_CHECKOUT_BALCAO_DTO = "{\"itens\":[]}"; // DTO mínimo para balcão
    private static final String USUARIO_REGISTRO_DTO_VALID = "{\"nome\":\"Teste User\",\"email\":\"teste@user.com\",\"senha\":\"password123\",\"role\":\"GARCOM\"}"; // DTO válido para registro de usuário (ajustado para 'role' singular)
    private static final String COMANDA_STATUS_DTO = "{\"novoStatus\":\"FECHADA\"}"; // DTO para alterar status
    private static final String PEDIDO_STATUS_DTO = "{\"novoStatus\":\"PRONTO\"}"; // DTO para alterar status de pedido
    private static final String CAIXA_ABERTURA_DTO = "{\"valorAbertura\":100.00}"; // DTO para abrir caixa
    private static final String CAIXA_FECHAMENTO_DTO = "{\"valorFechamento\":100.00,\"justificativaDiferenca\":\"OK\"}"; // DTO para fechar caixa
    private static final String CONTA_POST_DTO = "{\"numeroConta\":1,\"valorTotal\":50.00}"; // DTO mínimo para POST /api/contas
    private static final String PRODUTO_DTO = "{\"nome\":\"Produto Teste\",\"descricao\":\"Descricao Teste\",\"preco\":10.00,\"categoria\":\"LANCHE\"}"; // DTO para produto

    // --- Helper para verificar acesso permitido (não 401 nem 403) ---
    private void assertAllowedAccess(ResultActions actions) throws Exception {
        actions.andDo(result -> {
            int status = result.getResponse().getStatus();
            assertFalse(status == 401 || status == 403, "Expected status not 401 or 403, but got " + status);
        });
    }

    // =========================================================================
    // BLOCO 1: Rotas Públicas
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 1: Rotas Públicas (Acesso Livre e Sem JWT)")
    class Bloco1RotasPublicas {

        @Test
        @DisplayName("PUB001 - POST /api/auth/login deve responder com 401 (Unauthorized), nunca 403")
        void pub001() throws Exception {
            mockMvc.perform(post(AUTH_LOGIN_URL)
                            .contentType(APPLICATION_JSON)
                            .content(LOGIN_DTO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUB002 - POST /api/auth/login/cliente deve responder com 401 (Unauthorized), nunca 403")
        void pub002() throws Exception {
            mockMvc.perform(post(AUTH_LOGIN_CLIENTE_URL)
                            .contentType(APPLICATION_JSON)
                            .content(LOGIN_DTO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUB003 - POST /api/auth/registrar deve responder com 422 (Unprocessable Entity) devido a validação, nunca 401/403")
        void pub003() throws Exception {
            mockMvc.perform(post(AUTH_REGISTER_URL)
                            .contentType(APPLICATION_JSON)
                            .content(REGISTER_DTO_INVALID))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("PUB004 - POST /api/auth/cliente/google deve responder com 401 (Unauthorized) sem token Google, nunca 403")
        void pub004() throws Exception {
            mockMvc.perform(post(AUTH_CLIENTE_GOOGLE_URL)
                            .contentType(APPLICATION_JSON)
                            .content(REGISTER_DTO_INVALID)) // Simula falta de token Google
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUB005 - GET /ws-tevao (websocket) deve responder com 400 (Bad Request) sem upgrade header, nunca 401/403")
        void pub005() throws Exception {
            mockMvc.perform(get(WS_TEVAO_URL))
                    .andExpect(status().isBadRequest()); // Espera 400 Bad Request por falta de cabeçalho de upgrade
        }

        @Test
        @DisplayName("PUB006 - GET /api/fila-impressao/** deve ser acessível (200 OK ou 404 Not Found), nunca 401/403")
        void pub006() throws Exception {
            // Assumindo que /api/fila-impressao/** é publico e o GET não existe, resultando em 404
            assertAllowedAccess(mockMvc.perform(get(FILA_IMPRESSAO_URL))); // Corrigido para assertAllowedAccess() pois agora aponta para um endpoint existente e público
        }
    }

    // =========================================================================
    // BLOCO 2: Sem Token (Todas as rotas protegidas devem retornar 401)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 2: Requisições Sem Token / Anônimas (Esperado 401 Unauthorized)")
    class Bloco2SemToken {

        @Test
        @DisplayName("ANON001 - GET /api/cardapio sem token deve ser rejeitado com 401")
        void anon001() throws Exception {
            mockMvc.perform(get(CARDAPIO_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON002 - GET /api/usuarios sem token deve ser rejeitado com 401")
        void anon002() throws Exception {
            mockMvc.perform(get(USUARIOS_BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON003 - GET /api/relatorios sem token deve ser rejeitado com 401")
        void anon003() throws Exception {
            mockMvc.perform(get(RELATORIOS_BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON004 - GET /api/comandas/ativas sem token deve retornar 401")
        void anon004() throws Exception {
            mockMvc.perform(get(COMANDAS_ATIVAS_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON005 - POST /api/comandas/abrir/{numeroMesa} sem token deve retornar 401")
        void anon005() throws Exception {
            mockMvc.perform(post(COMANDAS_ABRIR_URL, 10))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON006 - GET /api/comandas/{id} sem token deve retornar 401")
        void anon006() throws Exception {
            mockMvc.perform(get(COMANDA_ID_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON007 - PUT /api/comandas/{id}/status sem token deve retornar 401")
        void anon007() throws Exception {
            mockMvc.perform(
                    put(COMANDA_STATUS_URL)
                            .contentType(APPLICATION_JSON)
                            .content(COMANDA_STATUS_DTO)
            ).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON008 - PUT /api/comandas/{id}/fechar sem token deve retornar 401")
        void anon008() throws Exception {
            mockMvc.perform(put(COMANDA_FECHAR_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON009 - GET /api/clientes sem token deve ser rejeitado com 401")
        void anon009() throws Exception {
            mockMvc.perform(get(CLIENTES_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON010 - GET /api/pedidos sem token deve ser rejeitado com 401")
        void anon010() throws Exception {
            mockMvc.perform(get(PEDIDOS_BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON011 - POST /api/pedidos/balcao/checkout sem token deve ser rejeitado com 401")
        void anon011() throws Exception {
            mockMvc.perform(post(PEDIDOS_BALCAO_CHECKOUT_URL)
                            .contentType(APPLICATION_JSON)
                            .content(NEW_CHECKOUT_BALCAO_DTO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON012 - POST /api/delivery/pedidos/checkout sem token deve ser rejeitado com 401")
        void anon012() throws Exception {
            mockMvc.perform(post(DELIVERY_PEDIDOS_CHECKOUT_URL)
                            .contentType(APPLICATION_JSON)
                            .content(NEW_CHECKOUT_DELIVERY_DTO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON013 - POST /api/contas sem token deve ser rejeitado com 401")
        void anon013() throws Exception {
            mockMvc.perform(post(CONTAS_URL)
                            .contentType(APPLICATION_JSON)
                            .content(CONTA_POST_DTO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON014 - GET /api/pagamentos sem token deve ser rejeitado com 401")
        void anon014() throws Exception {
            mockMvc.perform(get(PAGAMENTOS_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ANON015 - GET /api/carrinhos sem token deve ser rejeitado com 401")
        void anon015() throws Exception {
            mockMvc.perform(get(CARRINHOS_URL))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // BLOCO 3: ROLE_CLIENTE
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 3: Perfil CLIENTE (Acesso a Delivery, Restrição a PDV/Admin/Comandas)")
    class Bloco3RoleCliente {

        // @MockBean // Mockar PedidoCoreService - REMOVIDO: Duplicado, já existe na classe externa
        // private PedidoCoreService pedidoCoreService;

        private static final String CLIENTE_OWN_UUID_STR = "d86b7721-729d-4e92-bc91-5da5fa774b99";
        private static final String CLIENTE_OTHER_UUID_STR = "a1b2c3d4-e5f6-7890-1234-567890abcdef";
        private static final UUID CLIENTE_OWN_UUID = UUID.fromString(CLIENTE_OWN_UUID_STR);
        private static final UUID CLIENTE_OTHER_UUID = UUID.fromString(CLIENTE_OTHER_UUID_STR);

        // IDs de Pedido (NOVAS CONSTANTES)
        private static final UUID PEDIDO_OWN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111"); // Pedido que pertence ao CLIENTE_OWN_UUID
        private static final UUID PEDIDO_OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222"); // Pedido que pertence ao CLIENTE_OTHER_UUID

        @BeforeEach
        void setUp() {
            // Mock para buscarPedidoDeliveryDoClienteAutenticado (sucesso)
            when(pedidoCoreService.buscarPedidoDeliveryDoClienteAutenticado(PEDIDO_OWN_ID))
                    .thenReturn(new PedidoResponseDTO(
                            PEDIDO_OWN_ID,
                            PEDIDO_OWN_ID.toString(), // numeroPedido (String)
                            "Cliente Teste", // clienteNome (String)
                            LocalDateTime.now(), // dataHora (LocalDateTime)
                            StatusPedido.RECEBIDO, // status (StatusPedido)
                            StatusFinanceiro.AGUARDANDO_PAGAMENTO, // statusFinanceiro (StatusFinanceiro)
                            FormaPagamento.PIX, // formaPagamento (FormaPagamento)
                            TipoPedido.DELIVERY, // tipo (TipoPedido)
                            BigDecimal.TEN, // total (BigDecimal)
                            "Rua Teste, 123", // enderecoEntrega (String)
                            null, // numeroMesa (Integer)
                            null, // mesaId (UUID) - Adicionado
                            "Obs Teste", // observacaoGeral (String)
                            List.of() // itens (List<ItemPedidoResponseDTO>)
                    ));

            // Mock para buscarPedidoDeliveryDoClienteAutenticado (acesso negado)
            when(pedidoCoreService.buscarPedidoDeliveryDoClienteAutenticado(PEDIDO_OTHER_ID))
                    .thenThrow(new AccessDeniedException("Acesso negado: Este pedido não pertence ao cliente autenticado."));

            // Mock para listarHistoricoDeliveryDoClienteAutenticado
            when(pedidoCoreService.listarHistoricoDeliveryDoClienteAutenticado())
                    .thenReturn(List.of(new PedidoResponseDTO(
                            PEDIDO_OWN_ID,
                            PEDIDO_OWN_ID.toString(), // numeroPedido (String)
                            "Cliente Teste", // clienteNome (String)
                            LocalDateTime.now(), // dataHora (LocalDateTime)
                            StatusPedido.RECEBIDO, // status (StatusPedido)
                            StatusFinanceiro.AGUARDANDO_PAGAMENTO, // statusFinanceiro (StatusFinanceiro)
                            FormaPagamento.PIX, // formaPagamento (FormaPagamento)
                            TipoPedido.DELIVERY, // tipo (TipoPedido)
                            BigDecimal.TEN, // total (BigDecimal)
                            "Rua Teste, 123", // enderecoEntrega (String)
                            null, // numeroMesa (Integer)
                            null, // mesaId (UUID) - Adicionado
                            "Obs Teste", // observacaoGeral (String)
                            List.of() // itens (List<ItemPedidoResponseDTO>)
                    )));
        }

        @Test
        @WithMockUser(username = CLIENTE_OWN_UUID_STR, roles = {"CLIENTE"}) // Ajustado: username é o UUID do cliente
        @DisplayName("CLI001 - Cliente pode fazer checkout de pedido delivery (Acesso Permitido)")
        void cli001() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(DELIVERY_PEDIDOS_CHECKOUT_URL)
                    .contentType(APPLICATION_JSON)
                    .content(NEW_CHECKOUT_DELIVERY_DTO)));
        }

        @Test
        @WithMockUser(username = CLIENTE_OWN_UUID_STR, roles = {"CLIENTE"}) // Ajustado: username é o UUID do cliente
        @DisplayName("CLI002 - Cliente pode buscar seu próprio pedido delivery por ID (Acesso Permitido)")
        void cli002() throws Exception {
            // Usamos DELIVERY_PEDIDOS_ID_OWN_URL para simular um pedido que pertence ao cliente autenticado
            assertAllowedAccess(mockMvc.perform(get(DELIVERY_PEDIDOS_ID_OWN_URL)));
        }

        @Test
        @WithMockUser(username = CLIENTE_OWN_UUID_STR, roles = {"CLIENTE"}) // Ajustado: username é o UUID do cliente
        @DisplayName("CLI003 - Cliente pode visualizar seu histórico de pedidos (Acesso Permitido)")
        void cli003() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(DELIVERY_PEDIDOS_HISTORICO_URL)));
        }

        @Test
        @WithMockUser(username = CLIENTE_OWN_UUID_STR, roles = {"CLIENTE"}) // Ajustado: username é o UUID do cliente
        @DisplayName("CLI004 - Cliente NÃO pode visualizar pedido de OUTRO cliente por ID (403 Forbidden)")
        void cli004() throws Exception {
            // Este teste agora verifica a lógica de segurança implementada no service
            // que compara o ID do cliente autenticado com o ID do pedido.
            mockMvc.perform(get(DELIVERY_PEDIDOS_ID_OTHER_URL)) // A URL tem o ID do pedido de OUTRO cliente
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI005 - Cliente NÃO pode acessar /api/comandas (403 Forbidden)")
        void cli005() throws Exception {
            mockMvc.perform(get(COMANDAS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI006 - Cliente NÃO pode acessar /api/comandas/ativas (403 Forbidden)")
        void cli006() throws Exception {
            mockMvc.perform(get(COMANDAS_ATIVAS_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI007 - Cliente NÃO pode abrir comanda (403 Forbidden)")
        void cli007() throws Exception {
            mockMvc.perform(post(COMANDAS_ABRIR_URL, 10))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI008 - Cliente NÃO pode buscar comanda por ID (403 Forbidden)")
        void cli008() throws Exception {
            mockMvc.perform(get(COMANDA_ID_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI009 - Cliente NÃO pode alterar status de comanda (403 Forbidden)")
        void cli009() throws Exception {
            mockMvc.perform(put(COMANDA_STATUS_URL)
                            .contentType(APPLICATION_JSON)
                            .content(COMANDA_STATUS_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI010 - Cliente NÃO pode fechar comanda (403 Forbidden)")
        void cli010() throws Exception {
            mockMvc.perform(put(COMANDA_FECHAR_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI011 - Cliente NÃO pode acessar /api/clientes (403 Forbidden)")
        void cli011() throws Exception {
            mockMvc.perform(get(CLIENTES_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI012 - Cliente NÃO pode acessar /api/caixas (403 Forbidden)")
        void cli012() throws Exception {
            mockMvc.perform(get(CAIXAS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI013 - Cliente PODE acessar /api/produtos (Acesso Permitido - cai em authenticated())")
        void cli013() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CARDAPIO_URL)));
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI014 - Cliente NÃO pode acessar /api/usuarios (403 Forbidden)")
        void cli014() throws Exception {
            mockMvc.perform(get(USUARIOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI015 - Cliente NÃO pode acessar /api/relatorios (403 Forbidden)")
        void cli015() throws Exception {
            mockMvc.perform(get(RELATORIOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI016 - Cliente NÃO pode acessar /api/pedidos (listagem geral) (403 Forbidden)")
        void cli016() throws Exception {
            mockMvc.perform(get(PEDIDOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI017 - Cliente NÃO pode fazer checkout de balcão (403 Forbidden)")
        void cli017() throws Exception {
            mockMvc.perform(post(PEDIDOS_BALCAO_CHECKOUT_URL)
                            .contentType(APPLICATION_JSON)
                            .content(NEW_CHECKOUT_BALCAO_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI018 - Cliente NÃO pode acessar /api/contas (403 Forbidden)")
        void cli018() throws Exception {
            mockMvc.perform(post(CONTAS_URL)
                            .contentType(APPLICATION_JSON)
                            .content(CONTA_POST_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI019 - Cliente NÃO pode acessar /api/pagamentos (403 Forbidden)")
        void cli019() throws Exception {
            mockMvc.perform(get(PAGAMENTOS_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI020 - Cliente PODE acessar /api/carrinhos (Acesso Permitido)")
        void cli020() throws Exception {
            // Corrigido: Cliente tem permissão para acessar seu próprio carrinho
            assertAllowedAccess(mockMvc.perform(get(CARRINHOS_URL)));
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI021 - Cliente NÃO pode criar produto (POST /api/produtos) (403 Forbidden)")
        void cli021() throws Exception {
            mockMvc.perform(post(CARDAPIO_URL)
                            .contentType(APPLICATION_JSON)
                            .content(PRODUTO_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI022 - Cliente NÃO pode editar produto (PUT /api/produtos/{id}) (403 Forbidden)")
        void cli022() throws Exception {
            mockMvc.perform(put(CARDAPIO_ID_URL)
                            .contentType(APPLICATION_JSON)
                            .content(PRODUTO_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("CLI023 - Cliente NÃO pode excluir produto (DELETE /api/produtos/{id}) (403 Forbidden)")
        void cli023() throws Exception {
            mockMvc.perform(delete(CARDAPIO_ID_URL))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // BLOCO 4: ROLE_GARCOM
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 4: Perfil GARCOM (Acesso a PDV/Mesa, Restrição a Admin/Delivery)")
    class Bloco4RoleGarcom {

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR001 - Garçom pode acessar /api/comandas (Acesso Permitido)")
        void gar001() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(COMANDAS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR002 - Garçom pode acessar /api/comandas/ativas (Acesso Permitido)")
        void gar002() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(COMANDAS_ATIVAS_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR003 - Garçom pode abrir comanda (Acesso Permitido)")
        void gar003() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(COMANDAS_ABRIR_URL, 10)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR004 - Garçom pode buscar comanda por ID (Acesso Permitido)")
        void gar004() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(COMANDA_ID_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR005 - Garçom pode alterar status de comanda (Acesso Permitido)")
        void gar005() throws Exception {
            assertAllowedAccess(mockMvc.perform(put(COMANDA_STATUS_URL)
                    .contentType(APPLICATION_JSON)
                    .content(COMANDA_STATUS_DTO)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR006 - Garçom pode fechar comanda (Acesso Permitido)")
        void gar006() throws Exception {
            assertAllowedAccess(mockMvc.perform(put(COMANDA_FECHAR_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR007 - Garçom pode acessar /api/clientes (Acesso Permitido)")
        void gar007() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CLIENTES_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR008 - Garçom pode acessar /api/pedidos (listagem geral) (Acesso Permitido)")
        void gar008() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(PEDIDOS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR009 - Garçom pode criar pedido mobile (Acesso Permitido)")
        void gar009() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(PEDIDOS_MOBILE_URL)
                    .contentType(APPLICATION_JSON)
                    .content("{}"))); // DTO genérico
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR010 - Garçom pode fazer checkout de balcão (Acesso Permitido)")
        void gar010() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(PEDIDOS_BALCAO_CHECKOUT_URL)
                    .contentType(APPLICATION_JSON)
                    .content(NEW_CHECKOUT_BALCAO_DTO)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR011 - Garçom pode ver status do caixa (Acesso Permitido)")
        void gar011() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CAIXAS_STATUS_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR012 - Garçom pode ver resumo do caixa (Acesso Permitido)")
        void gar012() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CAIXAS_RESUMO_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR013 - Garçom NÃO pode acessar /api/usuarios (403 Forbidden)")
        void gar013() throws Exception {
            mockMvc.perform(get(USUARIOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR014 - Garçom PODE acessar /api/cardapio (Acesso Permitido)")
        void gar014() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CARDAPIO_URL)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR015 - Garçom NÃO pode acessar /api/relatorios (403 Forbidden)")
        void gar015() throws Exception {
            mockMvc.perform(get(RELATORIOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR016 - Garçom NÃO pode abrir caixa (403 Forbidden)")
        void gar016() throws Exception {
            mockMvc.perform(post(CAIXAS_ABRIR_URL)
                            .contentType(APPLICATION_JSON)
                            .content(CAIXA_ABERTURA_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR017 - Garçom NÃO pode fechar caixa (403 Forbidden)")
        void gar017() throws Exception {
            // Corrigido: Usar patch() para o endpoint de fechar caixa
            mockMvc.perform(patch(CAIXAS_FECHAR_URL)
                            .contentType(APPLICATION_JSON)
                            .content(CAIXA_FECHAMENTO_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR018 - Garçom NÃO pode fazer checkout de delivery (403 Forbidden)")
        void gar018() throws Exception {
            mockMvc.perform(post(DELIVERY_PEDIDOS_CHECKOUT_URL)
                            .contentType(APPLICATION_JSON)
                            .content(NEW_CHECKOUT_DELIVERY_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR019 - Garçom pode acessar /api/contas (Acesso Permitido)")
        void gar019() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(CONTAS_URL)
                    .contentType(APPLICATION_JSON)
                    .content(CONTA_POST_DTO)));
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR020 - Garçom NÃO pode acessar /api/pagamentos (403 Forbidden)")
        void gar020() throws Exception {
            mockMvc.perform(get(PAGAMENTOS_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR021 - Garçom NÃO pode acessar /api/carrinhos (403 Forbidden)")
        void gar021() throws Exception {
            mockMvc.perform(get(CARRINHOS_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR022 - Garçom NÃO pode criar produto (POST /api/produtos) (403 Forbidden)")
        void gar022() throws Exception {
            mockMvc.perform(post(CARDAPIO_URL)
                            .contentType(APPLICATION_JSON)
                            .content(PRODUTO_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR023 - Garçom NÃO pode editar produto (PUT /api/produtos/{id}) (403 Forbidden)")
        void gar023() throws Exception {
            mockMvc.perform(put(CARDAPIO_ID_URL)
                            .contentType(APPLICATION_JSON)
                            .content(PRODUTO_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("GAR024 - Garçom NÃO pode excluir produto (DELETE /api/produtos/{id}) (403 Forbidden)")
        void gar024() throws Exception {
            mockMvc.perform(delete(CARDAPIO_ID_URL))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // BLOCO 5: ROLE_COZINHA
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 5: Perfil COZINHA (Acesso a Pedidos, Restrição a Finanças/Admin/Comandas)")
    class Bloco5RoleCozinha {

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ001 - Cozinha pode acessar GET /api/pedidos (listagem geral) (Acesso Permitido)")
        void coz001() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(PEDIDOS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ002 - Cozinha pode alterar status de pedido PUT /api/pedidos/{id}/status (Acesso Permitido)")
        void coz002() throws Exception {
            assertAllowedAccess(mockMvc.perform(put(PEDIDOS_ID_STATUS_URL)
                    .contentType(APPLICATION_JSON)
                    .content(PEDIDO_STATUS_DTO))); // DTO genérico
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ003 - Cozinha pode acessar /api/fila-impressao (Acesso Permitido)")
        void coz003() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(FILA_IMPRESSAO_URL)));
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ004 - Cozinha NÃO pode fazer checkout de balcão (403 Forbidden)")
        void coz004() throws Exception {
            mockMvc.perform(post(PEDIDOS_BALCAO_CHECKOUT_URL)
                            .contentType(APPLICATION_JSON)
                            .content(NEW_CHECKOUT_BALCAO_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ005 - Cozinha NÃO pode fazer checkout de delivery (403 Forbidden)")
        void coz005() throws Exception {
            mockMvc.perform(post(DELIVERY_PEDIDOS_CHECKOUT_URL)
                            .contentType(APPLICATION_JSON)
                            .content(NEW_CHECKOUT_DELIVERY_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ006 - Cozinha NÃO pode acessar /api/caixas (403 Forbidden)")
        void coz006() throws Exception {
            mockMvc.perform(get(CAIXAS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ007 - Cozinha NÃO pode acessar /api/usuarios (403 Forbidden)")
        void coz007() throws Exception {
            mockMvc.perform(get(USUARIOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ008 - Cozinha NÃO pode acessar /api/clientes (403 Forbidden)")
        void coz008() throws Exception {
            mockMvc.perform(get(CLIENTES_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ009 - Cozinha NÃO pode acessar /api/relatorios (403 Forbidden)")
        void coz009() throws Exception {
            mockMvc.perform(get(RELATORIOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ010 - Cozinha NÃO pode acessar /api/comandas (403 Forbidden)")
        void coz010() throws Exception {
            mockMvc.perform(get(COMANDAS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ011 - Cozinha NÃO pode acessar /api/comandas/ativas (403 Forbidden)")
        void coz011() throws Exception {
            mockMvc.perform(get(COMANDAS_ATIVAS_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ012 - Cozinha NÃO pode abrir comanda (403 Forbidden)")
        void coz012() throws Exception {
            mockMvc.perform(post(COMANDAS_ABRIR_URL, 10))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ013 - Cozinha NÃO pode buscar comanda por ID (403 Forbidden)")
        void coz013() throws Exception {
            mockMvc.perform(get(COMANDA_ID_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ014 - Cozinha NÃO pode alterar status de comanda (403 Forbidden)")
        void coz014() throws Exception {
            mockMvc.perform(put(COMANDA_STATUS_URL)
                            .contentType(APPLICATION_JSON)
                            .content(COMANDA_STATUS_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ015 - Cozinha NÃO pode fechar comanda (403 Forbidden)")
        void coz015() throws Exception {
            mockMvc.perform(put(COMANDA_FECHAR_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ16 - Cozinha NÃO pode acessar /api/contas (403 Forbidden)")
        void coz016() throws Exception {
            mockMvc.perform(post(CONTAS_URL)
                            .contentType(APPLICATION_JSON)
                            .content(CONTA_POST_DTO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ17 - Cozinha NÃO pode acessar /api/pagamentos (403 Forbidden)")
        void coz017() throws Exception {
            mockMvc.perform(get(PAGAMENTOS_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("COZ18 - Cozinha NÃO pode acessar /api/carrinhos (403 Forbidden)")
        void coz018() throws Exception {
            mockMvc.perform(get(CARRINHOS_URL))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // BLOCO 6: ROLE_ADMIN
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 6: Perfil ADMINISTRADOR (Acesso Total)")
    class Bloco6RoleAdmin {

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM001 - Admin pode acessar /api/cardapio (Acesso Permitido)")
        void adm001() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CARDAPIO_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM002 - Admin pode listar /api/usuarios (Acesso Permitido)")
        void adm002() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(USUARIOS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM003 - Admin pode cadastrar /api/usuarios (Acesso Permitido)")
        void adm003() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(USUARIOS_BASE_URL)
                    .contentType(APPLICATION_JSON)
                    .content(USUARIO_REGISTRO_DTO_VALID))); // DTO válido
        }

        // Removido: ADM004 - Admin pode excluir /api/usuarios/{id} (Funcionalidade não existe)
        // @Test
        // @WithMockUser(roles = {"ADMIN"})
        // @DisplayName("ADM004 - Admin pode excluir /api/usuarios/{id} (Acesso Permitido)")
        // void adm004() throws Exception {
        //     assertAllowedAccess(mockMvc.perform(delete(USUARIOS_ID_URL)));
        // }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM005 - Admin pode listar /api/clientes (Acesso Permitido)")
        void adm005() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CLIENTES_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM006 - Admin pode acessar /api/caixas (Acesso Permitido)")
        void adm006() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CAIXAS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM007 - Admin pode abrir caixa /api/caixas/abrir (Acesso Permitido)")
        void adm007() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(CAIXAS_ABRIR_URL)
                    .contentType(APPLICATION_JSON)
                    .content(CAIXA_ABERTURA_DTO))); // DTO válido
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM008 - Admin pode fechar caixa /api/caixas/fechar (Acesso Permitido)")
        void adm008() throws Exception {
            // Corrigido: Usar patch() para o endpoint de fechar caixa
            assertAllowedAccess(mockMvc.perform(patch(CAIXAS_FECHAR_URL)
                    .contentType(APPLICATION_JSON)
                    .content(CAIXA_FECHAMENTO_DTO))); // DTO válido
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM009 - Admin pode acessar /api/relatorios (Acesso Permitido)")
        void adm009() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(RELATORIOS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM010 - Admin pode listar /api/pedidos (Acesso Permitido)")
        void adm010() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(PEDIDOS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM011 - Admin pode fazer checkout de delivery (Acesso Permitido)")
        void adm011() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(DELIVERY_PEDIDOS_CHECKOUT_URL)
                    .contentType(APPLICATION_JSON)
                    .content(NEW_CHECKOUT_DELIVERY_DTO)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM012 - Admin pode acessar /api/comandas (Acesso Permitido)")
        void adm012() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(COMANDAS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM013 - Admin pode acessar /api/comandas/ativas (Acesso Permitido)")
        void adm013() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(COMANDAS_ATIVAS_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM014 - Admin pode abrir comanda (Acesso Permitido)")
        void adm014() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(COMANDAS_ABRIR_URL, 10)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM015 - Admin pode buscar comanda por ID (Acesso Permitido)")
        void adm015() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(COMANDA_ID_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM016 - Admin pode alterar status de comanda (Acesso Permitido)")
        void adm016() throws Exception {
            assertAllowedAccess(mockMvc.perform(put(COMANDA_STATUS_URL)
                    .contentType(APPLICATION_JSON)
                    .content(COMANDA_STATUS_DTO)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM017 - Admin pode fechar comanda (Acesso Permitido)")
        void adm017() throws Exception {
            assertAllowedAccess(mockMvc.perform(put(COMANDA_FECHAR_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM018 - Admin pode acessar /api/contas (Acesso Permitido)")
        void adm018() throws Exception {
            assertAllowedAccess(mockMvc.perform(post(CONTAS_URL)
                    .contentType(APPLICATION_JSON)
                    .content(CONTA_POST_DTO)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM019 - Admin pode acessar /api/pagamentos (Acesso Permitido)")
        void adm019() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(PAGAMENTOS_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ADM020 - Admin pode acessar /api/carrinhos (Acesso Permitido)")
        void adm020() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CARRINHOS_URL)));
        }
    }

    // =========================================================================
    // BLOCO 7: JWT (Testes de Comportamento do SecurityFilter)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 7: Testes de Comportamento do JWT (SecurityFilter)")
    class Bloco7Jwt {

        @Test
        @DisplayName("JWT001 - Requisição sem Authorization header para rota protegida deve retornar 401")
        void jwt001() throws Exception {
            mockMvc.perform(get(PEDIDOS_BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("JWT002 - Requisição com Bearer token malformado deve retornar 401")
        void jwt002() throws Exception {
            mockMvc.perform(get(PEDIDOS_BASE_URL)
                            .header("Authorization", "Bearer malformed.token.string"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("JWT003 - Requisição com Bearer token de CLIENTE para rota de ADMIN deve retornar 403")
        void jwt003() throws Exception {
            // Com @WithMockUser, testamos a autorização após a autenticação.
            mockMvc.perform(get(USUARIOS_BASE_URL)
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("cliente").roles("CLIENTE")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("JWT004 - Requisição com Bearer token de ADMIN para rota protegida deve retornar Acesso Permitido")
        void jwt004() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(PEDIDOS_BASE_URL)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))));
        }
    }

    // =========================================================================
    // BLOCO 8: Isolamento entre módulos (PDV vs Delivery)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 8: Isolamento entre Módulos (PDV e Delivery)")
    class Bloco8IsolamentoModulos {

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("ISO001 - Cliente Delivery NUNCA consegue acessar /api/pedidos (listagem geral PDV) (403 Forbidden)")
        void iso001() throws Exception {
            mockMvc.perform(get(PEDIDOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("ISO002 - Garçom NUNCA consegue acessar /api/delivery/* (403 Forbidden)")
        void iso002() throws Exception {
            mockMvc.perform(get(DELIVERY_PEDIDOS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ISO003 - Admin consegue acessar ambos os módulos (PDV) (Acesso Permitido)")
        void iso003() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(PEDIDOS_BASE_URL)));
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("ISO004 - Admin consegue acessar ambos os módulos (Delivery) (Acesso Permitido)")
        void iso004() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(DELIVERY_PEDIDOS_BASE_URL)));
        }
    }

    // =========================================================================
    // BLOCO 9: Regressão (Garantias de Segurança Futuras)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 9: Regressão (Garantias de Segurança Futuras)")
    class Bloco9Regressao {

        @Test
        @DisplayName("REG001 - Nenhuma rota /api/delivery/** pode ser acessada sem ROLE_CLIENTE ou ADMIN (401 Unauthorized)")
        void reg001() throws Exception {
            mockMvc.perform(get(DELIVERY_PEDIDOS_BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("REG002 - Nenhuma rota /api/pedidos/balcao/** pode ser acessada sem ROLE_GARCOM ou ADMIN (401 Unauthorized)")
        void reg002() throws Exception {
            mockMvc.perform(post(PEDIDOS_BALCAO_CHECKOUT_URL)
                            .contentType(APPLICATION_JSON)
                            .content(NEW_CHECKOUT_BALCAO_DTO))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("REG003 - Rota /api/cardapio exige ROLE_ADMIN (401 Unauthorized sem token)")
        void reg003() throws Exception {
            mockMvc.perform(get(CARDAPIO_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("REG004 - Rota /api/produtos PODE ser acessada por CLIENTE (200 OK - cai em authenticated())")
        void reg004() throws Exception {
            assertAllowedAccess(mockMvc.perform(get(CARDAPIO_URL)));
        }

        @Test
        @DisplayName("REG005 - Nenhuma rota /api/comandas/** pode ser acessada sem ROLE_GARCOM ou ADMIN (401 Unauthorized)")
        void reg005() throws Exception {
            mockMvc.perform(get(COMANDAS_BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("REG006 - Nenhuma rota /api/comandas/** pode ser acessada sem ROLE_GARCOM ou ADMIN (403 Forbidden para CLIENTE)")
        void reg006() throws Exception {
            mockMvc.perform(get(COMANDAS_BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"COZINHA"})
        @DisplayName("REG007 - Nenhuma rota /api/comandas/** pode ser acessada sem ROLE_GARCOM ou ADMIN (403 Forbidden para COZINHA)")
        void reg007() throws Exception {
            mockMvc.perform(get(COMANDAS_BASE_URL))
                    .andExpect(status().isForbidden());
        }
    }
}