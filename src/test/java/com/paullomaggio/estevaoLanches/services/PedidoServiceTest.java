package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoStatusRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private CarrinhoRepository carrinhoRepository;
    @Mock private CaixaRepository caixaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private AdicionalRepository adicionalRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private Cliente cliente;
    private Carrinho carrinho;
    private Pedido pedidoPadrao;
    private ItemPedido itemPedidoExistente;
    private Produto prodA, prodB;
    private Adicional adicionalCheddar;
    private UUID clienteId, pedidoId, prodAId, prodBId, itemExistenteId, adicionalId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();
        prodAId = UUID.randomUUID();
        prodBId = UUID.randomUUID();
        itemExistenteId = UUID.randomUUID();
        adicionalId = UUID.randomUUID();

        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("Maria Santos");

        prodA = new Produto(); prodA.setId(prodAId); prodA.setPreco(new BigDecimal("10.00")); prodA.setNome("X-Bacon"); prodA.setPrecisaPreparo(true);
        prodB = new Produto(); prodB.setId(prodBId); prodB.setPreco(new BigDecimal("20.00")); prodB.setNome("X-Tudo"); prodB.setPrecisaPreparo(true);

        adicionalCheddar = new Adicional();
        adicionalCheddar.setId(adicionalId);
        adicionalCheddar.setNome("Queijo Cheddar");
        adicionalCheddar.setPreco(new BigDecimal("3.50"));

        carrinho = new Carrinho();
        carrinho.setCliente(cliente);
        carrinho.setItens(new ArrayList<>());
        ItemCarrinho item1 = new ItemCarrinho(); item1.setProduto(prodA); item1.setQuantidade(2); item1.setObservacao("Sem cebola");
        carrinho.getItens().add(item1);

        pedidoPadrao = new Pedido();
        pedidoPadrao.setId(pedidoId);
        pedidoPadrao.setCliente(cliente);
        pedidoPadrao.setStatus(StatusPedido.RECEBIDO);
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedidoPadrao.setTipo(TipoPedido.DELIVERY);
        pedidoPadrao.setTotal(new BigDecimal("20.00"));
        pedidoPadrao.setDataHora(LocalDateTime.now());
        pedidoPadrao.setNumeroPedido("TEST1");
        pedidoPadrao.setItens(new ArrayList<>());

        itemPedidoExistente = new ItemPedido();
        itemPedidoExistente.setId(itemExistenteId);
        itemPedidoExistente.setProduto(prodA);
        itemPedidoExistente.setQuantidade(2);
        itemPedidoExistente.setPrecoUnitario(new BigDecimal("10.00"));
        itemPedidoExistente.setPedido(pedidoPadrao);
        pedidoPadrao.getItens().add(itemPedidoExistente);
    }

    @Test
    @DisplayName("Testes 1 a 8: Checkout Geral, Limpeza de Carrinho e Status Pago no Balcão")
    void deveFinalizarCheckoutsDiversosComSucesso() {
        // Possui FormaPagamento, então deve nascer PAGO
        CheckoutRequestDTO dtoApp = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, "Rua A", null, null, null, null, FormaPagamento.CREDITO, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoApp);

        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(resultado.statusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(carrinho.getItens()).isEmpty();
    }

    @Test
    @DisplayName("Novo Teste: Deve finalizar checkout de Mesa sem pagamento e gerar status AGUARDANDO_PAGAMENTO")
    void deveFinalizarCheckoutSemFormaPagamentoEGerarStatusAguardando() {
        // Forma de pagamento enviada como NULL
        CheckoutRequestDTO dtoMesa = new CheckoutRequestDTO(clienteId, TipoPedido.MESA, null, 5, null, null, null, null, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoMesa);

        assertThat(resultado.statusFinanceiro()).isEqualTo(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("Testes 9 a 12: Exceções em Checkouts (Caixa Fechado, Sem Carrinho, etc)")
    void deveLancarExcecoesRegrasDeNegocioNoCheckout() {
        CheckoutRequestDTO dto = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, null, null, null, null, null, FormaPagamento.PIX, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);
        assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(dto));

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);

        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.finalizarPedido(dto));

        carrinho.getItens().clear();
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(dto));
    }

    // =========================================================================
    // NOVOS TESTES: FLUXO DE PAGAMENTO SEPARADO (StatusFinanceiro)
    // =========================================================================

    @Test
    @DisplayName("Novo Teste: Deve receber pagamento de pedido em aberto e mudar para PAGO")
    void deveReceberPagamentoComSucesso() {
        PagamentoRequestDTO pagamentoDTO = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("20.00"));

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.receberPagamento(pedidoId, pagamentoDTO);

        assertThat(res.statusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(res.formaPagamento()).isEqualTo(FormaPagamento.PIX);
    }

    @Test
    @DisplayName("Novo Teste: Deve impedir duplo pagamento no mesmo pedido")
    void deveImpedirReceberPagamentoDuplicado() {
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.PAGO);
        PagamentoRequestDTO pagamentoDTO = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("20.00"));

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                pedidoService.receberPagamento(pedidoId, pagamentoDTO)
        );
        assertThat(exception.getMessage()).isEqualTo("Este pedido ja consta como PAGO.");
    }

    @Test
    @DisplayName("Novo Teste: Deve impedir pagamento de um pedido que foi cancelado")
    void deveImpedirReceberPagamentoDePedidoCancelado() {
        pedidoPadrao.setStatus(StatusPedido.CANCELADO);
        PagamentoRequestDTO pagamentoDTO = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("20.00"));

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                pedidoService.receberPagamento(pedidoId, pagamentoDTO)
        );
        assertThat(exception.getMessage()).isEqualTo("Nao e possivel receber pagamento de um pedido cancelado.");
    }

    @Test
    @DisplayName("Novo Teste: Deve alterar StatusFinanceiro para ESTORNADO ao cancelar um pedido PAGO")
    void deveAlterarStatusFinanceiroParaEstornadoAoCancelarPedidoPago() {
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.PAGO);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.cancelarPedido(pedidoId);

        assertThat(res.status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(res.statusFinanceiro()).isEqualTo(StatusFinanceiro.ESTORNADO);
    }

    @Test
    @DisplayName("Novo Teste: Deve impedir edição de itens se o pedido já estiver PAGO")
    void deveImpedirEdicaoDeItensSePedidoEstiverPago() {
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.PAGO);
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, null, null);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                pedidoService.adicionarItemPedido(pedidoId, novoItem)
        );
        assertThat(exception.getMessage()).isEqualTo("Nao e possivel alterar os itens de um pedido que ja foi pago.");
    }

    // =========================================================================
    // RESTANTE DOS TESTES DE LISTAGEM E OPERAÇÃO
    // =========================================================================

    @Test
    @DisplayName("Testes 19 a 22: Buscar Pedido e Listar Todos")
    void deveBuscarEListarTodosOsPedidos() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        assertThat(pedidoService.buscarPorId(pedidoId).id()).isEqualTo(pedidoId);

        when(pedidoRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.buscarPorId(UUID.randomUUID()));

        when(pedidoRepository.findAll()).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarTodos()).hasSize(1);

        when(pedidoRepository.findAll()).thenReturn(Collections.emptyList());
        assertThat(pedidoService.listarTodos()).isEmpty();
    }

    @Test
    @DisplayName("Testes 23 a 26: Histórico do Cliente e Monitor da Cozinha")
    void deveListarHistoricoEMonitor() {
        when(pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId)).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarHistoricoCliente(clienteId)).hasSize(1);

        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(anyList())).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarPedidosAtivosMonitor()).hasSize(1);

        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(anyList())).thenReturn(Collections.emptyList());
        assertThat(pedidoService.listarPedidosAtivosMonitor()).isEmpty();
    }

    @Test
    @DisplayName("Testes 27 a 30: Deve atualizar status seguindo o fluxo normal")
    void deveAtualizarStatusComSucesso() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.atualizarStatus(pedidoId, new PedidoStatusRequestDTO(StatusPedido.EM_PREPARO));
        assertThat(res.status()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    @Test
    @DisplayName("Testes 34 a 39: Deve permitir cancelar pedidos abertos e impedir finalizados")
    void deveCancelarPedidosCorretamente() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.cancelarPedido(pedidoId);

        assertThat(res.status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(res.statusFinanceiro()).isEqualTo(StatusFinanceiro.CANCELADO);

        pedidoPadrao.setStatus(StatusPedido.FINALIZADO);
        assertThrows(BusinessRuleException.class, () -> pedidoService.cancelarPedido(pedidoId));
    }

    @Test
    @DisplayName("Testes 40 e 41: Deve adicionar item e recalcular total (Em pedido não pago)")
    void deveAdicionarItemERecalcularTotal() {
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, "Adicional", null);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.adicionarItemPedido(pedidoId, novoItem);

        assertThat(res.total()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(pedidoPadrao.getItens()).hasSize(2);
    }
}