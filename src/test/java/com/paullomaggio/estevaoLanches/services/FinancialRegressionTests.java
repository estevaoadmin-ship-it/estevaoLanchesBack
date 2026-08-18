package com.paullomaggio.estevaoLanches.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Financial Regression Tests — Proteção contra regressões em cálculos financeiros")
class FinancialRegressionTests {

    // Mocks
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private AdicionalRepository adicionalRepository;
    @Mock private ItemComboRepository itemComboRepository;
    @Mock private ComboProdutoRepository comboProdutoRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private ComandaRepository comandaRepository;
    @Mock private PagamentoService pagamentoService;
    @Mock private ClienteRepository clienteRepository;
    @Mock private MesaRepository mesaRepository;
    @Mock private CarrinhoRepository carrinhoRepository;
    @Mock private CaixaRepository caixaRepository;
    @Mock private FilaImpressaoRepository filaImpressaoRepository;
    @Mock private AdicionalValidationService adicionalValidationService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    // Services under test
    private PedidoService pedidoService;
    @InjectMocks private ContaService contaService;
    @InjectMocks private GarcomMesaSessaoService garcomMesaSessaoService;

    // Test data
    private UUID produtoId;
    private UUID adicionalId1;
    private UUID adicionalId2;
    private UUID comboId;
    private UUID clienteId;
    private UUID pedidoId;
    private UUID contaId;
    private UUID comandaId;
    private Mesa mesa;
    private Comanda comanda;
    private Conta conta;
    private Cliente cliente;
    private Produto produto;
    private Produto produtoCombo;
    private Adicional adicional1;
    private Adicional adicional2;

    @BeforeEach
    void setUp() {
        // Initialize IDs
        produtoId = UUID.randomUUID();
        adicionalId1 = UUID.randomUUID();
        adicionalId2 = UUID.randomUUID();
        comboId = UUID.randomUUID();
        clienteId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();
        contaId = UUID.randomUUID();
        comandaId = UUID.randomUUID();

        // Initialize mesa
        mesa = new Mesa();
        mesa.setId(UUID.randomUUID());
        mesa.setNumero(5);
        mesa.setStatus(StatusMesa.OCUPADA);

        // Initialize comanda
        comanda = new Comanda();
        comanda.setId(comandaId);
        comanda.setMesa(mesa);
        comanda.setStatus(StatusComanda.ABERTA);
        comanda.setAbertaEm(LocalDateTime.now());
        comanda.setContas(new java.util.ArrayList<>());

        // Initialize conta
        conta = new Conta();
        conta.setId(contaId);
        conta.setNumeroConta(1);
        conta.setPago(false);
        conta.setValorTotal(BigDecimal.ZERO);
        conta.setComanda(comanda);
        conta.setCliente(new Cliente());

        // Initialize cliente
        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("TESTE CLIENTE");

        // Initialize produto normal
        produto = new Produto();
        produto.setId(produtoId);
        produto.setNome("Produto Teste Normal");
        produto.setPreco(new BigDecimal("25.00"));
        produto.setPrecisaPreparo(true);

        // Initialize produto combo
        produtoCombo = new Produto();
        produtoCombo.setId(comboId);
        produtoCombo.setNome("Combo Teste");
        produtoCombo.setPreco(new BigDecimal("35.00"));
        produtoCombo.setPrecisaPreparo(true);
        produtoCombo.setIsCombo(true);

        // Initialize adicionais
        adicional1 = new Adicional();
        adicional1.setId(adicionalId1);
        adicional1.setNome("Adicional 1");
        adicional1.setPreco(new BigDecimal("5.00"));

        adicional2 = new Adicional();
        adicional2.setId(adicionalId2);
        adicional2.setNome("Adicional 2");
        adicional2.setPreco(new BigDecimal("3.00"));

        // Construct PedidoService manually to inject contaService (real @InjectMocks)
        pedidoService = new PedidoService(
                pedidoRepository, carrinhoRepository, caixaRepository,
                produtoRepository, adicionalRepository, filaImpressaoRepository,
                comandaRepository, contaRepository, messagingTemplate,
                itemComboRepository, comboProdutoRepository,
                pagamentoService, adicionalValidationService,
                clienteRepository, contaService
        );
    }

    @Test
    @DisplayName("CT-FR-001: Produto normal sem adicional")
    void produtoNormalSemAdicional() {
        // Arrange
        Produto produto = new Produto();
        produto.setId(produtoId);
        produto.setNome("Produto Teste");
        produto.setPreco(new BigDecimal("25.00"));
        produto.setPrecisaPreparo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produto);
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("25.00"));
        item.setAdicionais(List.of());
        item.setObservacaoItem("");

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setPedidos(List.of(pedido));
        conta.setValorTotal(BigDecimal.ZERO);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert
        assertEquals(new BigDecimal("25.00"), result.total());
        assertEquals(new BigDecimal("25.00"), pedido.getTotal());
    }

    @Test
    @DisplayName("CT-FR-002: Produto normal com um adicional")
    void produtoNormalComUmAdicional() {
        // Arrange
        Produto produto = new Produto();
        produto.setId(produtoId);
        produto.setNome("Produto Teste");
        produto.setPreco(new BigDecimal("25.00"));
        produto.setPrecisaPreparo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produto);
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("30.00"));
        Adicional adicional = new Adicional();
        adicional.setId(adicionalId1);
        adicional.setNome("Adicional Teste");
        adicional.setPreco(new BigDecimal("5.00"));
        item.setAdicionais(List.of(adicional));
        item.setObservacaoItem("");

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setPedidos(List.of(pedido));
        conta.setValorTotal(BigDecimal.ZERO);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert
        assertEquals(new BigDecimal("30.00"), result.total());
        assertEquals(new BigDecimal("30.00"), pedido.getTotal());
    }

    @Test
    @DisplayName("CT-FR-003: Produto normal com múltiplos adicionais")
    void produtoNormalComMultiplosAdicionais() {
        // Arrange
        Produto produto = new Produto();
        produto.setId(produtoId);
        produto.setNome("Produto Teste");
        produto.setPreco(new BigDecimal("25.00"));
        produto.setPrecisaPreparo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produto);
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("33.00"));
        Adicional adicional1 = new Adicional();
        adicional1.setId(adicionalId1);
        adicional1.setNome("Adicional 1");
        adicional1.setPreco(new BigDecimal("5.00"));
        Adicional adicional2 = new Adicional();
        adicional2.setId(adicionalId2);
        adicional2.setNome("Adicional 2");
        adicional2.setPreco(new BigDecimal("3.00"));
        item.setAdicionais(List.of(adicional1, adicional2));
        item.setObservacaoItem("");

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setPedidos(List.of(pedido));
        conta.setValorTotal(BigDecimal.ZERO);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert
        assertEquals(new BigDecimal("33.00"), result.total());
        assertEquals(new BigDecimal("33.00"), pedido.getTotal());
    }

    @Test
    @DisplayName("CT-FR-004: Produto normal com quantidade")
    void produtoNormalComQuantidade() {
        // Arrange
        Produto produto = new Produto();
        produto.setId(produtoId);
        produto.setNome("Produto Teste");
        produto.setPreco(new BigDecimal("15.00"));
        produto.setPrecisaPreparo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produto);
        item.setQuantidade(3);
        item.setPrecoUnitario(new BigDecimal("17.00"));
        Adicional adicional = new Adicional();
        adicional.setId(adicionalId1);
        adicional.setNome("Adicional Teste");
        adicional.setPreco(new BigDecimal("2.00"));
        item.setAdicionais(List.of(adicional));
        item.setObservacaoItem("");

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setPedidos(List.of(pedido));
        conta.setValorTotal(BigDecimal.ZERO);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert: (15 + 2) × 3 = 51
        assertEquals(new BigDecimal("51.00"), result.total());
        assertEquals(new BigDecimal("51.00"), pedido.getTotal());
    }

    @Test
    @DisplayName("CT-FR-005: Combo sem adicional")
    void comboSemAdicional() {
        // Arrange
        Produto produtoCombo = new Produto();
        produtoCombo.setId(comboId);
        produtoCombo.setNome("Combo Teste");
        produtoCombo.setPreco(new BigDecimal("35.00"));
        produtoCombo.setPrecisaPreparo(true);
        produtoCombo.setIsCombo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produtoCombo);
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("35.00"));
        item.setAdicionais(List.of());
        item.setObservacaoItem("");

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setPedidos(List.of(pedido));
        conta.setValorTotal(BigDecimal.ZERO);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);
        when(itemComboRepository.findByItemPedidoId(item.getId())).thenReturn(List.of());

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert
        assertEquals(new BigDecimal("35.00"), result.total());
        assertEquals(new BigDecimal("35.00"), pedido.getTotal());
    }

    @Test
    @DisplayName("CT-FR-006: Combo com adicional interno - NÃO somar preço do produto interno")
    void comboComAdicionalInternoNaoSomarPrecoProdutoInterno() {
        // Arrange
        Produto produtoCombo = new Produto();
        produtoCombo.setId(comboId);
        produtoCombo.setNome("Combo Teste");
        produtoCombo.setPreco(new BigDecimal("35.00"));
        produtoCombo.setPrecisaPreparo(true);
        produtoCombo.setIsCombo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produtoCombo);
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("35.00")); // This is the combo price
        item.setAdicionais(List.of());
        item.setObservacaoItem("");

        // Create ItemCombo (snapshot of internal product)
        ItemCombo itemCombo = new ItemCombo();
        itemCombo.setId(UUID.randomUUID());
        itemCombo.setItemPedido(item);
        itemCombo.setProdutoId(UUID.randomUUID()); // Internal product ID
        itemCombo.setNomeProduto("Produto Interno Teste"); // Informational only
        itemCombo.setQuantidade(1);
        itemCombo.setPrecoUnitario(new BigDecimal("10.00")); // INFORMATIONAL - should NOT be added to total
        Adicional adicionalInterno = new Adicional();
        adicionalInterno.setId(UUID.randomUUID());
        adicionalInterno.setNome("Adicional Interno");
        adicionalInterno.setPreco(new BigDecimal("5.00")); // This SHOULD be added
        itemCombo.setAdicionais(List.of(adicionalInterno));

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setPedidos(List.of(pedido));
        conta.setValorTotal(BigDecimal.ZERO);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);
        when(itemComboRepository.findByItemPedidoId(item.getId())).thenReturn(List.of(itemCombo));

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert: Combo price (35.00) + internal adicional (5.00) = 40.00
        // IMPORTANTE: The internal product price (10.00) should NOT be included
        assertEquals(new BigDecimal("40.00"), result.total());
        assertEquals(new BigDecimal("40.00"), pedido.getTotal());
        
        // CRITICAL ASSERTION: Ensure we did NOT sum the internal product price
        // 35 (combo) + 10 (internal product) + 5 (adicional) = 50 would be WRONG
        assertNotEquals(new BigDecimal("50.00"), result.total(),
                "Erro crítico: O preço do produto interno do combo está sendo somado indevidamente!");
    }

    @Test
    @DisplayName("CT-FR-007: Combo com quantidade e adicional interno")
    void comboComQuantidadeEAdicionalInterno() {
        // Arrange
        Produto produtoCombo = new Produto();
        produtoCombo.setId(comboId);
        produtoCombo.setNome("Combo Teste");
        produtoCombo.setPreco(new BigDecimal("35.00"));
        produtoCombo.setPrecisaPreparo(true);
        produtoCombo.setIsCombo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produtoCombo);
        item.setQuantidade(2); // Quantity 2
        item.setPrecoUnitario(new BigDecimal("35.00")); // Combo price
        item.setAdicionais(List.of());
        item.setObservacaoItem("");

        // Create ItemCombo (snapshot of internal product)
        ItemCombo itemCombo = new ItemCombo();
        itemCombo.setId(UUID.randomUUID());
        itemCombo.setItemPedido(item);
        itemCombo.setProdutoId(UUID.randomUUID()); // Internal product ID
        itemCombo.setNomeProduto("Produto Interno Teste"); // Informational only
        itemCombo.setQuantidade(1);
        itemCombo.setPrecoUnitario(new BigDecimal("10.00")); // INFORMATIONAL - should NOT be added to total
        Adicional adicionalInterno = new Adicional();
        adicionalInterno.setId(UUID.randomUUID());
        adicionalInterno.setNome("Adicional Interno");
        adicionalInterno.setPreco(new BigDecimal("5.00")); // This SHOULD be added
        itemCombo.setAdicionais(List.of(adicionalInterno));

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setPedidos(List.of(pedido));
        conta.setValorTotal(BigDecimal.ZERO);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);
        when(itemComboRepository.findByItemPedidoId(item.getId())).thenReturn(List.of(itemCombo));

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert: (Combo price 35.00 + internal adicional 5.00) × quantity 2 = 80.00
        // (35 + 5) × 2 = 80
        // IMPORTANTE: The internal product price (10.00) should NOT be included
        assertEquals(new BigDecimal("80.00"), result.total());
        assertEquals(new BigDecimal("80.00"), pedido.getTotal());
        
        // CRITICAL ASSERTIONS
        // Wrong calculation 1: (35 + 10 + 5) × 2 = 100
        assertNotEquals(new BigDecimal("100.00"), result.total(),
                "Erro crítico: O preço do produto interno do combo está sendo somado indevidamente!");
        // Wrong calculation 2: 35 × 2 + 5 = 75 (esqueceu de multiplicar o adicional pela quantidade)
        assertNotEquals(new BigDecimal("75.00"), result.total(),
                "Erro crítico: O adicional não está sendo multiplicado pela quantidade do combo!");
    }

    @Test
    @DisplayName("CT-FR-008: Conta com pedido cancelado - não deve contribuir para valor total")
    void contaComPedidoCanceladoNaoContribuiParaValorTotal() {
        // Arrange
        Pedido pedidoAtivo = new Pedido();
        pedidoAtivo.setId(UUID.randomUUID());
        pedidoAtivo.setStatus(StatusPedido.EM_PREPARO);
        pedidoAtivo.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedidoAtivo.setTipo(TipoPedido.MESA);
        pedidoAtivo.setTotal(new BigDecimal("50.00"));
        pedidoAtivo.setConta(conta);
        pedidoAtivo.setCliente(cliente);

        Pedido pedidoCancelado = new Pedido();
        pedidoCancelado.setId(UUID.randomUUID());
        pedidoCancelado.setStatus(StatusPedido.CANCELADO);
        pedidoCancelado.setStatusFinanceiro(StatusFinanceiro.CANCELADO);
        pedidoCancelado.setTipo(TipoPedido.MESA);
        pedidoCancelado.setTotal(new BigDecimal("30.00")); // Historic value preserved
        pedidoCancelado.setConta(conta);
        pedidoCancelado.setCliente(cliente);

        conta.setId(contaId);
        conta.setNumeroConta(1);
        conta.setPago(false);
        conta.setValorTotal(BigDecimal.ZERO); // Will be calculated
        conta.setComanda(comanda);
        conta.setCliente(cliente);
        conta.setPedidos(List.of(pedidoAtivo, pedidoCancelado));

        // Mock repository behavior
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));

        // Act
        contaService.sincronizarValorTotal(contaId);

        // Assert
        // Only the active pedido should contribute: 50.00
        // The canceled pedido should NOT contribute: 30.00 (ignored)
        assertEquals(new BigDecimal("50.00"), conta.getValorTotal());
        
        // Historic values should be preserved
        assertEquals(new BigDecimal("50.00"), pedidoAtivo.getTotal());
        assertEquals(new BigDecimal("30.00"), pedidoCancelado.getTotal());
        
        // CRITICAL ASSERTION: Ensure canceled pedido is NOT included
        // If incorrectly included, total would be 50.00 + 30.00 = 80.00
        assertNotEquals(new BigDecimal("80.00"), conta.getValorTotal(),
                "Erro crítico: Pedido cancelado está sendo incluído no valor total da conta!");
    }

    @Test
    @DisplayName("CT-FR-009: Preservação de histórico - alteração no catálogo não afeta pedidos existentes")
    void preservacaoHistoricoAlteracaoCatalogoNaoAfetaPedidosExistentes() {
        // Arrange
        // Create product with original price
        Produto produtoOriginal = new Produto();
        produtoOriginal.setId(produtoId);
        produtoOriginal.setNome("Produto Teste");
        produtoOriginal.setPreco(new BigDecimal("20.00")); // Original price
        produtoOriginal.setPrecisaPreparo(true);

        // Create pedido with historical price
        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produtoOriginal);
        item.setQuantidade(2);
        item.setPrecoUnitario(new BigDecimal("20.00")); // Historical price - should be preserved
        item.setAdicionais(List.of());
        item.setObservacaoItem("");

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setId(contaId);
        conta.setNumeroConta(1);
        conta.setPago(false);
        conta.setValorTotal(BigDecimal.ZERO);
        conta.setComanda(comanda);
        conta.setCliente(cliente);
        conta.setPedidos(List.of(pedido));

        // Mock repository behavior - when we look up the product NOW, it has a different price
        // But the pedido should still use its historical price
        Produto produtoAtualizado = new Produto();
        produtoAtualizado.setId(produtoId);
        produtoAtualizado.setNome("Produto Teste");
        produtoAtualizado.setPreco(new BigDecimal("30.00")); // Price increased!
        produtoAtualizado.setPrecisaPreparo(true);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);

        // Act
        PedidoResponseDTO result = pedidoService.recalcularTotalPedido(pedidoId);

        // Assert
        // Should use historical price: 20.00 × 2 = 40.00
        // NOT current price: 30.00 × 2 = 60.00
        assertEquals(new BigDecimal("40.00"), result.total());
        assertEquals(new BigDecimal("40.00"), pedido.getTotal());
        
        // CRITICAL ASSERTION: Ensure we're NOT using the current catalog price
        assertNotEquals(new BigDecimal("60.00"), result.total(),
                "Erro crítico: O pedido está usando o preço atual do catálogo em vez do preço histórico!");
    }

    @Test
    @DisplayName("CT-FR-010: Consistência financeira entre Pedido, Conta e Sessão")
    void consistenciaFinanceiraEntrePedidoContaESessao() {
        // Arrange
        Produto produtoCombo = new Produto();
        produtoCombo.setId(comboId);
        produtoCombo.setNome("Combo Teste");
        produtoCombo.setPreco(new BigDecimal("35.00"));
        produtoCombo.setPrecisaPreparo(true);
        produtoCombo.setIsCombo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produtoCombo);
        item.setQuantidade(1);
        item.setPrecoUnitario(new BigDecimal("35.00")); // Combo price
        item.setAdicionais(List.of());
        item.setObservacaoItem("");

        // Create ItemCombo (snapshot of internal product) - adicional should be included
        ItemCombo itemCombo = new ItemCombo();
        itemCombo.setId(UUID.randomUUID());
        itemCombo.setItemPedido(item);
        itemCombo.setProdutoId(UUID.randomUUID()); // Internal product ID
        itemCombo.setNomeProduto("Produto Interno Teste"); // Informational only
        itemCombo.setQuantidade(1);
        itemCombo.setPrecoUnitario(new BigDecimal("10.00")); // INFORMATIONAL - should NOT be added to total
        Adicional adicionalInterno = new Adicional();
        adicionalInterno.setId(UUID.randomUUID());
        adicionalInterno.setNome("Adicional Interno");
        adicionalInterno.setPreco(new BigDecimal("5.00")); // This SHOULD be added
        itemCombo.setAdicionais(List.of(adicionalInterno));

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setConta(conta);
        pedido.setItens(List.of(item));
        item.setPedido(pedido);

        conta.setId(contaId);
        conta.setNumeroConta(1);
        conta.setPago(false);
        conta.setValorTotal(BigDecimal.ZERO);
        conta.setComanda(comanda);
        conta.setCliente(cliente);
        conta.setPedidos(List.of(pedido));

        comanda.setId(comandaId);
        comanda.setMesa(mesa);
        comanda.setStatus(StatusComanda.ABERTA);
        comanda.setAbertaEm(LocalDateTime.now());
        comanda.setContas(List.of(conta));

        mesa.setId(mesa.getId());
        mesa.setNumero(5);
        mesa.setStatus(StatusMesa.OCUPADA);

        // Mock repository behavior
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenReturn(pedido);
        when(contaRepository.findById(contaId)).thenReturn(Optional.of(conta));
        when(contaRepository.save(any())).thenReturn(conta);
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));
        when(mesaRepository.findById(mesa.getId())).thenReturn(Optional.of(mesa));
        when(itemComboRepository.findByItemPedidoId(item.getId())).thenReturn(List.of(itemCombo));
        when(itemComboRepository.findByItemPedidoIdIn(anyList())).thenReturn(List.of(itemCombo));

        // Act
        PedidoResponseDTO pedidoResult = pedidoService.recalcularTotalPedido(pedidoId);
        
        // Update conta total after pedido calculation
        contaService.sincronizarValorTotal(contaId);
        
        GarcomMesaSessaoResponseDTO sessaoResult = garcomMesaSessaoService.obterSessao(mesa.getId());

        // Assert
        // Expected: Combo price (35.00) + internal adicional (5.00) = 40.00
        BigDecimal expectedTotal = new BigDecimal("40.00");
        
        // Pedido total
        assertEquals(expectedTotal, pedidoResult.total());
        assertEquals(expectedTotal, pedido.getTotal());
        
        // Conta total
        assertEquals(expectedTotal, conta.getValorTotal());
        
        // Sessão item total
        assertEquals(1, sessaoResult.contas().size());
        assertEquals(1, sessaoResult.contas().get(0).itens().size());
        assertEquals(expectedTotal, sessaoResult.contas().get(0).itens().get(0).valorTotal());
        
        // Sessão conta total
        assertEquals(expectedTotal, sessaoResult.contas().get(0).valorTotal());
        
        // CRITICAL ASSERTIONS: Ensure internal product price is NOT included
        assertNotEquals(new BigDecimal("50.00"), pedidoResult.total(),
                "Erro crítico: O preço do produto interno do combo está sendo somado indevidamente no pedido!");
        assertNotEquals(new BigDecimal("50.00"), conta.getValorTotal(),
                "Erro crítico: O preço do produto interno do combo está sendo somado indevidamente na conta!");
        assertNotEquals(new BigDecimal("50.00"), sessaoResult.contas().get(0).itens().get(0).valorTotal(),
                "Erro crítico: O preço do produto interno do combo está sendo somado indevidamente na sessão!");
    }
}