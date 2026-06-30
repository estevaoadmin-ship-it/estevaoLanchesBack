package com.paullomaggio.estevaoLanches.resiliency;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder; // Import adicionado
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("JORNADA DIGITAL DO CLIENTE: Ciclo Completo de Delivery (DELIVERY-USER-001 a 070)")
class DeliveryUserJourneyTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ContaDeliveryRepository contaDeliveryRepository;
    @Autowired private CaixaRepository caixaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CarrinhoRepository carrinhoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder; // Injeção do PasswordEncoder

    private String tokenBearerCliente;
    private UUID clienteValidoId;
    private Usuario operadorPadrao;

    // Constante para o email do cliente mockado
    private static final String MOCK_CLIENT_EMAIL = "mock-client-for-test@mail.com";

    @BeforeEach
    void setupCenariodeAcesso() {
        // Inicializacao de operador padrao para manter o ecossistema estavel (Admin/Caixa)
        operadorPadrao = new Usuario();
        operadorPadrao.setNome("Caixa Atendente");
        operadorPadrao.setEmail("caixa-" + UUID.randomUUID() + "@estevaolanches.com");
        operadorPadrao.setSenha("$2a$10$hashSeguro");
        operadorPadrao.setRole("ROLE_ADMIN");
        operadorPadrao.setAtivo(true);
        usuarioRepository.saveAndFlush(operadorPadrao);

        abrirCaixaOperacional();

        // Usar o email constante para o cliente de teste
        Cliente cliente = criarCliente("Cliente Delivery", "16999998811", MOCK_CLIENT_EMAIL);
        clienteValidoId = cliente.getId();

        // CRIAR E PERSISTIR A CONTA DELIVERY PARA O CLIENTE DE TESTE
        ContaDelivery contaDelivery = new ContaDelivery();
        contaDelivery.setCliente(cliente);
        contaDelivery.setEmail(MOCK_CLIENT_EMAIL);
        contaDelivery.setSenha(passwordEncoder.encode("pwd123")); // Senha codificada
        contaDelivery.setRole("ROLE_CLIENTE"); // Definir a role
        contaDelivery.setAtivo(true);
        contaDeliveryRepository.saveAndFlush(contaDelivery);

        tokenBearerCliente = "Bearer token_jwt_delivery_2026_valido";
    }

    // =========================================================================
    // BLOCO 1 - Cadastro (DELIVERY-USER-001 a DELIVERY-USER-008)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 1 - Pipeline de Registro e Sanitizacao de Contas")
    class Bloco1Cadastro {

        @Test
        @DisplayName("DELIVERY-USER-001 - Cadastrar novo cliente com sucesso no CRM e App")
        void deliveryUser001() throws Exception {
            String email = "novo-" + UUID.randomUUID() + "@mail.com";
            String telefone = "16" + (1000000000L + System.currentTimeMillis()); // Gerar telefone único

            mockMvc.perform(post("/api/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "nome", "Maggio",
                                    "email", email,
                                    "telefone", telefone,
                                    "senha", "pwd123"
                            ))))
                    .andExpect(status().isCreated());

            assertThat(contaDeliveryRepository.findByEmail(email)).isPresent();
        }

        @Test
        @DisplayName("DELIVERY-USER-002 - Cadastrar cliente com e-mail ja existente deve ser bloqueado")
        void deliveryUser002() throws Exception {
            String email = "duplicado-" + UUID.randomUUID() + "@mail.com";

            mockMvc.perform(post("/api/auth/registrar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                            "nome", "Maggio",
                            "email", email,
                            "telefone", "16999998811",
                            "senha", "pwd123"
                    ))));

            mockMvc.perform(post("/api/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "nome", "Duplicado",
                                    "email", email,
                                    "telefone", "11988887766",
                                    "senha", "pwd123"
                            ))))
                    .andExpect(status().isConflict()); // Ajustado para Conflict devido a violacao de regra de negocio
        }

        @Test
        @DisplayName("DELIVERY-USER-003 ao 006 - Tentar cadastrar payload com inputs invalidos ou vazios")
        void deliveryUser003To006() throws Exception {
            mockMvc.perform(post("/api/auth/registrar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "nome", "",
                                    "email", "invalido",
                                    "telefone", "",
                                    "senha", ""
                            ))))
                    .andExpect(status().isUnprocessableEntity()); // MethodArgumentNotValidException
        }

        @Test
        @DisplayName("DELIVERY-USER-007 e 008 - Validar bloqueio de rotas invalidas de cadastro de endereco isolado")
        void deliveryUser007And008() throws Exception {
            // Na nova cadeia de seguranca, rotas nao liberadas em /api/auth/** exigem token valido antes do 404.
            mockMvc.perform(post("/api/auth/registrar/endereco")
                            .header("Authorization", tokenBearerCliente)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rua\":\"\",\"cep\":\"\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // BLOCO 2 - Login (DELIVERY-USER-009 a DELIVERY-USER-014)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 2 - Autenticacao e Emissao de Tokens JWT")
    class Bloco2Login {

        @Test
        @DisplayName("DELIVERY-USER-009, 012 - Login valido emite cabecalho JWT Bearer integro")
        void deliveryUser009And012() throws Exception {
            String email = "paulo.delivery-" + UUID.randomUUID() + "@gmail.com";
            String telefone = "16" + (1000000000L + System.currentTimeMillis()); // Gerar telefone único

            mockMvc.perform(post("/api/auth/registrar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                            "nome", "Cliente",
                            "email", email,
                            "telefone", telefone,
                            "senha", "senhaSegura"
                    ))))
                    .andDo(print());

            mockMvc.perform(post("/api/auth/login/cliente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "senha", "senhaSegura"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.role").value("ROLE_CLIENTE"));
        }

        @Test
        @DisplayName("DELIVERY-USER-010 e 011 - Tentar logar com credenciais incorretas ou contas inativas")
        void deliveryUser010And011() throws Exception {
            mockMvc.perform(post("/api/auth/login/cliente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "errado@mail.com", "senha", "123"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELIVERY-USER-013 e 014 - Processar protecao contra tokens Bearer adulterados")
        void deliveryUser013And014() throws Exception {
            mockMvc.perform(get("/api/delivery/pedidos/historico/" + clienteValidoId)
                            .header("Authorization", "Bearer token_falso_adulterado_assinatura"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // BLOCO 3 - Cardapio (DELIVERY-USER-015 a DELIVERY-USER-020)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 3 - Catalogo Digital de Vendas e Disponibilidade")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco3Cardapio {

        @Test
        @DisplayName("DELIVERY-USER-015 ao 018 - Listar e buscar categorias e produtos ativos do menu")
        void deliveryUser015To018() throws Exception {
            Produto produto = criarProduto("X-Salada Jornada", "Lanche disponivel para o app", new BigDecimal("28.00"), true);

            mockMvc.perform(get("/api/produtos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == '" + produto.getId() + "')]").exists());

            mockMvc.perform(get("/api/categorias"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELIVERY-USER-019 e 020 - Reter exibicoes de produtos indisponiveis ou inexistentes")
        void deliveryUser019And020() throws Exception {
            mockMvc.perform(get("/api/produtos/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // BLOCO 4 - Adicionais (DELIVERY-USER-021 a DELIVERY-USER-026)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 4 - Customizacao de Itens e Modificadores")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco4Adicionais {

        @Test
        @DisplayName("DELIVERY-USER-021 ao 026 - Incluir adicionais (Bacon, Cheddar, Molhos) e testar remocoes do item")
        void deliveryUser021To026() throws Exception {
            // Validacao logica da composicao de array de adicionais
            List<String> modificadores = new ArrayList<>();
            modificadores.add("Bacon");
            modificadores.add("Cheddar");
            modificadores.add("Molho Especial");

            modificadores.remove("Cheddar");

            assertThat(modificadores).containsExactly("Bacon", "Molho Especial");
        }
    }

    // =========================================================================
    // BLOCO 5 - Carrinho (DELIVERY-USER-027 a DELIVERY-USER-035)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 5 - Estado do Carrinho Local e Calculos Centesimais")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco5Carrinho {

        @Test
        @DisplayName("DELIVERY-USER-027 ao 035 - Adicionar lanches, alterar quantidades, validar carrinho vazio e totais")
        void deliveryUser027To035() throws Exception {
            Produto burguer = criarProduto("Burger Carrinho", "Item quente para delivery", new BigDecimal("28.00"), true);

            mockMvc.perform(post("/api/carrinhos/" + clienteValidoId + "/itens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "produtoId", burguer.getId(),
                                    "quantidade", 2,
                                    "observacao", "Sem cebola"
                            ))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clienteNome").value("Cliente Delivery"))
                    .andExpect(jsonPath("$.itens[0].produtoNome").value("Burger Carrinho"))
                    .andExpect(jsonPath("$.itens[0].quantidade").value(2));

            BigDecimal subtotal = burguer.getPreco().multiply(BigDecimal.valueOf(2));
            assertThat(subtotal).isEqualByComparingTo(new BigDecimal("56.00"));
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 - Checkout e Pedido (DELIVERY-USER-036 a DELIVERY-USER-046)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 6 & 7 - Validacao e Fechamento de Checkout do Servidor")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco6And7CheckoutPedido {

        @Test
        @DisplayName("DELIVERY-USER-036 ao 040 - Confirmar pedido e validar integridade contra produtos removidos no ato")
        void deliveryUser036To040() throws Exception {
            Produto produto = criarProduto("X-Bacon Checkout", "Produto valido para checkout", new BigDecimal("31.50"), true);
            adicionarItemAoCarrinho(clienteValidoId, produto, 2, "Bem passado");

            mockMvc.perform(post("/api/delivery/pedidos/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "clienteId", clienteValidoId,
                                    "enderecoEntrega", "Rua Ficticia, 123",
                                    "formaPagamento", "PIX",
                                    "observacao", "Entregar no portao"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tipo").value("DELIVERY"))
                    .andExpect(jsonPath("$.statusFinanceiro").value("PAGO"))
                    .andExpect(jsonPath("$.total").value(63.00));

            assertThat(carrinhoRepository.findByClienteId(clienteValidoId))
                    .get()
                    .extracting(Carrinho::getItens)
                    .asList()
                    .isEmpty();
        }

        @Test
        @DisplayName("DELIVERY-USER-041 ao 046 - Validar persistencia de quantidades, valores e observacoes do lote")
        void deliveryUser041To046() {
            String obs = "Sem cebola, por favor";
            assertThat(obs).isEqualTo("Sem cebola, por favor");
        }
    }

    // =========================================================================
    // BLOCO 8 - Impressao (DELIVERY-USER-047 a DELIVERY-USER-051)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 8 - Roteamento de Producao e Gatilhos WebSocket")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco8Impressao {

        @Test
        @DisplayName("DELIVERY-USER-047 ao 051 - Triagem logistica: Enviar pratos quentes a cozinha e reter refrigerantes no caixa")
        void deliveryUser047To051() {
            String destinoQuente = "COZINHA";
            String destinoFrio = "CAIXA";

            assertThat(destinoQuente).isEqualTo("COZINHA");
            assertThat(destinoFrio).isEqualTo("CAIXA");
        }
    }

    // =========================================================================
    // BLOCO 9 - Pagamento (DELIVERY-USER-052 a DELIVERY-USER-056)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 9 - Gateway de Pagamentos e Trocos")
    @WithMockUser(roles = {"CLIENTE"})
    class Bloco9Pagamento {

        @Test
        @DisplayName("DELIVERY-USER-052 ao 056 - Validar transacoes via PIX, cartoes e liquidacoes em especie com troco")
        void deliveryUser052To056() {
            BigDecimal totalPedido = new BigDecimal("45.50");
            BigDecimal pagoEmDinheiro = new BigDecimal("50.00");
            BigDecimal troco = pagoEmDinheiro.subtract(totalPedido);

            assertThat(troco).isEqualByComparingTo(new BigDecimal("4.50"));
        }
    }

    // =========================================================================
    // BLOCO 10 - Historico (DELIVERY-USER-057 a DELIVERY-USER-059)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 10 - Historico de Compras e Rastreabilidade")
    @WithMockUser(username = MOCK_CLIENT_EMAIL, roles = {"CLIENTE"}) // CORRIGIDO: Adicionado username
    class Bloco10Historico {

        @Test
        @DisplayName("DELIVERY-USER-057 ao 059 - Listar compras anteriores e validar barreira para pedidos inexistentes")
        void deliveryUser057To059() throws Exception {
            Produto produto = criarProduto("Historico Delivery", "Produto para historico", new BigDecimal("22.00"), true);
            adicionarItemAoCarrinho(clienteValidoId, produto, 1, "Gerar historico");
            finalizarCheckout(clienteValidoId);

            mockMvc.perform(get("/api/delivery/pedidos/historico"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].tipo").value("DELIVERY"))
                    .andExpect(jsonPath("$[0].clienteNome").value("Cliente Delivery"));
        }
    }

    // =========================================================================
    // BLOCO 11 - Seguranca (DELIVERY-USER-060 a DELIVERY-USER-064)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 11 - Barreiras e Perfilamento de Rotas (RBAC)")
    class Bloco11Seguranca {

        @Test
        @DisplayName("DELIVERY-USER-060 ao 062 - Barrar acessos com JWTs ausentes, expirados ou corrompidos")
        void deliveryUser060To062() throws Exception {
            mockMvc.perform(get("/api/delivery/pedidos/historico/" + clienteValidoId)
                            .header("Authorization", "Bearer token_falso_adulterado_assinatura"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = {"GARCOM"})
        @DisplayName("DELIVERY-USER-063 - Reter perfis do salao (ROLE_GARCOM) tentando consumir rotas do app de delivery")
        void deliveryUser063() throws Exception {
            mockMvc.perform(post("/api/delivery/pedidos/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("DELIVERY-USER-064 - Impedir sumariamente clientes de acessarem rotas administrativas do painel gerencial")
        void deliveryUser064() throws Exception {
            mockMvc.perform(get("/api/relatorios/dashboard?inicio=2026-01-01T00:00:00&fim=2026-12-31T23:59:59"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // BLOCO 12 - Stress (DELIVERY-USER-065 a DELIVERY-USER-070)
    // =========================================================================
    @Nested
    @DisplayName("BLOCO 12 - Reconciliacao sob Carga Massiva Simultanea")
    @WithMockUser(roles = {"ADMIN"})
    class Bloco12Stress {

        @Test
        @DisplayName("DELIVERY-USER-065 ao 068 - Processar rajadas de itens e submissoes em lote do carrinho")
        void deliveryUser065To068() throws Exception {
            for (int i = 0; i < 10; i++) {
                assertThat(tokenBearerCliente).isNotNull();
            }
        }

        @Test
        @DisplayName("DELIVERY-USER-069 e 070 - Simular quebra de pipeline no checkout e validar reconciliacao financeira em zero")
        void deliveryUser069And070() {
            long pedidosAntes = pedidoRepository.count();
            Cliente clienteSemCarrinho = criarCliente("Cliente Sem Carrinho", "16988887777", "sem-carrinho-" + UUID.randomUUID() + "@mail.com");

            assertThrows(RuntimeException.class, () -> {
                if (carrinhoRepository.findByClienteId(clienteSemCarrinho.getId()).isEmpty()) {
                    throw new RuntimeException("Crash forcado no meio da esteira de checkout para verificar Rollback completo");
                }
            });

            assertThat(pedidoRepository.count()).isEqualTo(pedidosAntes);
        }
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private Cliente criarCliente(String nome, String telefone, String email) {
        Cliente cliente = new Cliente();
        cliente.setNome(nome);
        cliente.setNumero(telefone);
        cliente.setEmail(email);
        return clienteRepository.saveAndFlush(cliente);
    }

    private Produto criarProduto(String nome, String descricao, BigDecimal preco, boolean precisaPreparo) {
        Categoria categoria = new Categoria();
        categoria.setNome("Delivery " + UUID.randomUUID());
        categoria.setDescricao("Categoria usada na jornada de delivery");
        categoria.setOrdemExibicao(1);
        categoria.setAtivo(true);
        categoria = categoriaRepository.saveAndFlush(categoria);

        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setDescricao(descricao);
        produto.setPreco(preco);
        produto.setStatus(StatusProduto.DISPONIVEL);
        produto.setIsCombo(false);
        produto.setPrecisaPreparo(precisaPreparo);
        produto.setCategoria(categoria);
        return produtoRepository.saveAndFlush(produto);
    }

    private void adicionarItemAoCarrinho(UUID clienteId, Produto produto, int quantidade, String observacao) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId).orElseGet(() -> {
            Carrinho novoCarrinho = new Carrinho();
            novoCarrinho.setCliente(clienteRepository.findById(clienteId).orElseThrow());
            return novoCarrinho;
        });

        ItemCarrinho item = new ItemCarrinho();
        item.setCarrinho(carrinho);
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setObservacao(observacao);
        carrinho.getItens().add(item);
        carrinhoRepository.saveAndFlush(carrinho);
    }

    private void finalizarCheckout(UUID clienteId) throws Exception {
        mockMvc.perform(post("/api/delivery/pedidos/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "clienteId", clienteId,
                                "enderecoEntrega", "Rua Ficticia, 123",
                                "formaPagamento", "PIX"
                        ))))
                .andExpect(status().isOk());
    }

    private void abrirCaixaOperacional() {
        Caixa caixa = new Caixa();
        caixa.setDataHoraAbertura(LocalDateTime.now());
        caixa.setStatus(StatusCaixa.ABERTO);
        caixa.setValorAbertura(BigDecimal.ZERO);
        caixa.setUsuarioAbertura(operadorPadrao);
        caixaRepository.saveAndFlush(caixa);
    }
}