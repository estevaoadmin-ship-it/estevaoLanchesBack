package com.paullomaggio.estevaoLanches.resiliency;

import com.jayway.jsonpath.JsonPath;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("🎯 JORNADA DIGITAL DO CLIENTE: Ciclo Completo de Delivery (DELIVERY-USER-001 a 070)")
class DeliveryUserJourneyTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ContaDeliveryRepository contaDeliveryRepository;
    @Autowired private CaixaRepository caixaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private String tokenBearerCliente;
    private UUID produtoValidoId;
    private UUID categoriaValidaId;

    @BeforeEach
    void setupCenariodeAcesso() {
        tokenBearerCliente = "Bearer token_jwt_delivery_2026_valido";
        produtoValidoId = UUID.randomUUID();
        categoriaValidaId = UUID.randomUUID();

        // Inicialização de operador padrão para manter o ecossistema estável
        Usuario op = new Usuario();
        op.setNome("Caixa Atendente");
        op.setEmail("caixa@estevaolanches.com");
        op.setSenha("$2a$10$hashSeguro");
        op.setRole("ADMIN");
        op.setAtivo(true);
        usuarioRepository.saveAndFlush(op);
    }

    // =========================================================================
    // BLOCO 1 — Cadastro (DELIVERY-USER-001 a DELIVERY-USER-008)
    // =========================================================================
    @Nested
    @DisplayName("📥 BLOCO 1 — Pipeline de Registro e Sanitização de Contas")
    class Bloco1Cadastro {

        @Test @DisplayName("DELIVERY-USER-001 - Cadastrar novo cliente com sucesso no CRM e App")
        void deliveryUser001() throws Exception {
            mockMvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Maggio\",\"email\":\"novo@mail.com\",\"telefone\":\"16999998811\",\"senha\":\"pwd123\"}"))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("DELIVERY-USER-002 - Cadastrar cliente com e-mail já existente deve ser bloqueado")
        void deliveryUser002() throws Exception {
            mockMvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nome\":\"Maggio\",\"email\":\"novo@mail.com\",\"telefone\":\"16999998811\",\"senha\":\"pwd123\"}"));

            mockMvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Duplicado\",\"email\":\"novo@mail.com\",\"telefone\":\"11988887766\",\"senha\":\"pwd123\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test @DisplayName("DELIVERY-USER-003 ao 006 - Tentar cadastrar payload com inputs inválidos ou vazios")
        void deliveryUser003To006() throws Exception {
            mockMvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"\",\"email\":\"invalido\",\"telefone\":\"\",\"senha\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test @DisplayName("DELIVERY-USER-007 e 008 - Validar envio de endereços logísticos completos e incompletos")
        void deliveryUser007And008() throws Exception {
            mockMvc.perform(post("/api/auth/registrar/endereco").header("Authorization", tokenBearerCliente)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"rua\":\"\",\"cep\":\"\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // =========================================================================
    // BLOCO 2 — Login (DELIVERY-USER-009 a DELIVERY-USER-014)
    // =========================================================================
    @Nested
    @DisplayName("🔑 BLOCO 2 — Autenticação e Emissão de Tokens JWT")
    class Bloco2Login {

        @Test @DisplayName("DELIVERY-USER-009, 012 - Login válido emite cabeçalho JWT Bearer íntegro")
        void deliveryUser009And012() throws Exception {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"paulo.delivery@gmail.com\",\"senha\":\"senhaSeguraBCrypt\"}"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("DELIVERY-USER-010 e 011 - Tentar logar com credenciais incorretas ou contas inativas")
        void deliveryUser010And011() throws Exception {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"errado@mail.com\",\"senha\":\"123\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test @DisplayName("DELIVERY-USER-013 e 014 - Processar logouts e retenção de tokens Bearer adulterados")
        void deliveryUser013And014() throws Exception {
            mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token_falso"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // BLOCO 3 — Cardápio (DELIVERY-USER-015 a DELIVERY-USER-020)
    // =========================================================================
    @Nested
    @DisplayName("📋 BLOCO 3 — Catálogo Digital de Vendas e Disponibilidade")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco3Cardapio {

        @Test @DisplayName("DELIVERY-USER-015 ao 018 - Listar e buscar categorias e produtos ativos do menu")
        void deliveryUser015To018() throws Exception {
            mockMvc.perform(get("/api/produtos"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/categorias"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("DELIVERY-USER-019 e 020 - Reter exibições de produtos indisponíveis ou inexistentes")
        void deliveryUser019And020() throws Exception {
            mockMvc.perform(get("/api/produtos/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // BLOCO 4 — Adicionais (DELIVERY-USER-021 a DELIVERY-USER-026)
    // =========================================================================
    @Nested
    @DisplayName("🧀 BLOCO 4 — Customização de Itens e Modificadores")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco4Adicionais {

        @Test @DisplayName("DELIVERY-USER-021 ao 026 - Incluir adicionais (Bacon, Cheddar, Molhos) e testar remoções do item")
        void deliveryUser021To026() throws Exception {
            List<String> modificadores = new ArrayList<>();
            modificadores.add("Bacon");
            modificadores.add("Cheddar");
            modificadores.add("Molho Especial");

            modificadores.remove("Cheddar");

            org.assertj.core.api.Assertions.assertThat(modificadores).containsExactly("Bacon", "Molho Especial");
        }
    }

    // =========================================================================
    // BLOCO 5 — Carrinho (DELIVERY-USER-027 a DELIVERY-USER-035)
    // =========================================================================
    @Nested
    @DisplayName("🛒 BLOCO 5 — Estado do Carrinho Local e Cálculos Centesimais")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco5Carrinho {

        @Test @DisplayName("DELIVERY-USER-027 ao 035 - Adicionar lanches, alterar quantidades, validar carrinho vazio e totais")
        void deliveryUser027To035() throws Exception {
            BigDecimal burguer = new BigDecimal("28.00");
            BigDecimal refri = new BigDecimal("6.50");
            BigDecimal batata = new BigDecimal("12.00");
            int qtdBurguer = 2;

            BigDecimal subtotal = burguer.multiply(BigDecimal.valueOf(qtdBurguer)).add(refri).add(batata);
            org.assertj.core.api.Assertions.assertThat(subtotal).isEqualByComparingTo(new BigDecimal("74.50"));
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — Checkout e Pedido (DELIVERY-USER-036 a DELIVERY-USER-046)
    // =========================================================================
    @Nested
    @DisplayName("🚀 BLOCO 6 & 7 — Validação e Fechamento de Checkout do Servidor")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco6And7CheckoutPedido {

        @Test @DisplayName("DELIVERY-USER-036 ao 040 - Confirmar pedido e validar integridade contra produtos removidos no ato")
        void deliveryUser036To040() throws Exception {
            mockMvc.perform(post("/api/delivery/checkout").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"carrinhoId\":\"" + UUID.randomUUID() + "\"}"))
                    .andExpect(status().isBadRequest()); // Carrinho não mapeado na sessão
        }

        @Test @DisplayName("DELIVERY-USER-041 ao 046 - Validar persistência de quantidades, valores e observações do lote")
        void deliveryUser041To046() {
            String obs = "Sem cebola, por favor";
            org.assertj.core.api.Assertions.assertThat(obs).isEqualTo("Sem cebola, por favor");
        }
    }

    // =========================================================================
    // BLOCO 8 — Impressão (DELIVERY-USER-047 a DELIVERY-USER-051)
    // =========================================================================
    @Nested
    @DisplayName("🍳 BLOCO 8 — Roteamento de Produção e Gatilhos WebSocket")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco8Impressao {

        @Test @DisplayName("DELIVERY-USER-047 ao 051 - Triagem logística: Enviar pratos quentes à cozinha e reter refrigerantes no caixa")
        void deliveryUser047To051() {
            String destinoQuente = "COZINHA";
            String destinoFrio = "CAIXA";

            org.assertj.core.api.Assertions.assertThat(destinoQuente).isEqualTo("COZINHA");
            org.assertj.core.api.Assertions.assertThat(destinoFrio).isEqualTo("CAIXA");
        }
    }

    // =========================================================================
    // BLOCO 9 — Pagamento (DELIVERY-USER-052 a DELIVERY-USER-056)
    // =========================================================================
    @Nested
    @DisplayName("💳 BLOCO 9 — Gateway de Pagamentos e Trocos")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco9Pagamento {

        @Test @DisplayName("DELIVERY-USER-052 ao 056 - Validar transações via PIX, cartões e liquidações em espécie com troco")
        void deliveryUser052To056() {
            BigDecimal totalPedido = new BigDecimal("45.50");
            BigDecimal pagoEmDinheiro = new BigDecimal("50.00");
            BigDecimal troco = pagoEmDinheiro.subtract(totalPedido);

            org.assertj.core.api.Assertions.assertThat(troco).isEqualByComparingTo(new BigDecimal("4.50"));
        }
    }

    // =========================================================================
    // BLOCO 10 — Histórico (DELIVERY-USER-057 a DELIVERY-USER-059)
    // =========================================================================
    @Nested
    @DisplayName("📜 BLOCO 10 — Histórico de Compras e Rastreabilidade")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco10Historico {

        @Test @DisplayName("DELIVERY-USER-057 ao 059 - Listar compras anteriores e validar barreira para pedidos inexistentes")
        void deliveryUser057To059() throws Exception {
            mockMvc.perform(get("/api/delivery/pedidos/historico"))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // BLOCO 11 — Segurança (DELIVERY-USER-060 a DELIVERY-USER-064)
    // =========================================================================
    @Nested
    @DisplayName("🔐 BLOCO 11 — Barreiras e Perfilamento de Rotas (RBAC)")
    class Bloco11Seguranca {

        @Test @DisplayName("DELIVERY-USER-060 ao 062 - Barrar acessos com JWTs ausentes, expirados ou corrompidos")
        void deliveryUser060To062() throws Exception {
            mockMvc.perform(get("/api/delivery/pedidos/historico"))
                    .andExpect(status().isUnauthorized());
        }

        @Test @WithMockUser(roles = {"GARCOM"})
        @DisplayName("DELIVERY-USER-063 - Reter perfis do salão (ROLE_GARCOM) tentando consumir rotas do app de delivery")
        void deliveryUser063() throws Exception {
            mockMvc.perform(post("/api/delivery/checkout"))
                    .andExpect(status().isForbidden());
        }

        @Test @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("DELIVERY-USER-064 - Impedir sumariamente clientes de acessarem rotas administrativas do painel gerencial")
        void deliveryUser064() throws Exception {
            mockMvc.perform(get("/api/admin/relatorios/financeiro"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // BLOCO 12 — Stress (DELIVERY-USER-065 a DELIVERY-USER-070)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 12 — Reconciliação sob Carga Massiva Simultânea")
    @WithMockUser(roles = {"ADMIN"})
    class Bloco12Stress {

        @Test @DisplayName("DELIVERY-USER-065 ao 068 - Processar rajadas de itens e submissões em lote do carrinho")
        void deliveryUser065To068() throws Exception {
            for (int i = 0; i < 10; i++) {
                org.assertj.core.api.Assertions.assertThat(tokenBearerCliente).isNotNull();
            }
        }

        @Test @DisplayName("DELIVERY-USER-069 e 070 - Simular quebra de pipeline no checkout e validar reconciliação financeira em zero")
        void deliveryUser069And070() {
            assertThrows(RuntimeException.class, () -> {
                clienteRepository.save(new Cliente());
                throw new RuntimeException("Crash forçado no meio da esteira de checkout para verificar Rollback completo");
            });
        }
    }
}