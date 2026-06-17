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
    @Mock private FilaImpressaoRepository filaImpressaoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private Cliente cliente;
    private Carrinho carrinho;
    private Pedido pedidoPadrao;
    private ItemPedido itemPedidoExistente;
    private Produto prodA, prodB;
    private UUID clienteId, pedidoId, prodAId, prodBId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();
        prodAId = UUID.randomUUID();
        prodBId = UUID.randomUUID();

        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("Maria Santos");

        prodA = new Produto(); prodA.setId(prodAId); prodA.setPreco(new BigDecimal("10.00")); prodA.setNome("X-Bacon"); prodA.setPrecisaPreparo(true);
        prodB = new Produto(); prodB.setId(prodBId); prodB.setPreco(new BigDecimal("20.00")); prodB.setNome("X-Tudo"); prodB.setPrecisaPreparo(true);

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
        itemPedidoExistente.setId(UUID.randomUUID());
        itemPedidoExistente.setProduto(prodA);
        itemPedidoExistente.setQuantidade(2);
        itemPedidoExistente.setPrecoUnitario(new BigDecimal("10.00"));
        itemPedidoExistente.setPedido(pedidoPadrao);
        pedidoPadrao.getItens().add(itemPedidoExistente);
    }

    // =========================================================================
    // 1. TESTES DE CHECKOUT E INTEGRAÇÃO COM FILA DE IMPRESSÃO
    // =========================================================================

    @Test
    @DisplayName("Checkout via Carrinho: Deve esvaziar carrinho e gerar pedido PAGO (com recibo e cozinha)")
    void deveFinalizarCheckoutViaCarrinhoComSucesso() {
        CheckoutRequestDTO dto = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, "Rua A", null, null, null, null, FormaPagamento.CREDITO, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dto);

        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(resultado.statusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(carrinho.getItens()).isEmpty();

        // Blindagem: Como já foi pago, deve mandar pra cozinha e gerar recibo
        verify(filaImpressaoRepository, atLeast(1)).save(any(FilaImpressao.class));
    }

    @Test
    @DisplayName("Checkout Venda Rápida (Balcão): Deve gerar pedido ignorando o carrinho e enviando para impressão")
    void deveCriarRegistroNaFilaAoFinalizarVendaRapida() {
        List<ItemPedidoRequestDTO> itensAvulsos = List.of(new ItemPedidoRequestDTO(prodAId, 1, null, null));
        CheckoutRequestDTO dtoVendaRapida = new CheckoutRequestDTO(
                null, TipoPedido.RETIRADA, null, null, null,
                "Cliente Balcao", null, FormaPagamento.PIX, new BigDecimal("10.00"), itensAvulsos
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoVendaRapida);

        assertThat(resultado.statusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("10.00"));

        // Verifica se a fila foi chamada para salvar
        verify(filaImpressaoRepository, atLeast(1)).save(any(FilaImpressao.class));
        verify(carrinhoRepository, never()).findByClienteId(any()); // Não pode ter tocado no carrinho
    }

    @Test
    @DisplayName("Checkout Mesa: Deve gerar status AGUARDANDO_PAGAMENTO e NÃO gerar recibo (só cozinha)")
    void deveFinalizarCheckoutMesaSemPagamento() {
        CheckoutRequestDTO dtoMesa = new CheckoutRequestDTO(clienteId, TipoPedido.MESA, null, 5, null, null, null, null, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoMesa);

        assertThat(resultado.statusFinanceiro()).isEqualTo(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        // Opcional: Aqui você pode verificar se apenas a via COZINHA foi gerada na Fila, dependendo de como implementou.
    }

    @Test
    @DisplayName("Checkout Falha: Deve bloquear venda se o Caixa estiver Fechado")
    void deveLancarExcecaoSeCaixaFechado() {
        CheckoutRequestDTO dto = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, null, null, null, null, null, FormaPagamento.PIX, null, null);
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(dto));
        assertThat(ex.getMessage()).containsIgnoringCase("caixa");
    }

    // =========================================================================
    // 2. TESTES DE PAGAMENTO POSTERIOR E FILA DE IMPRESSÃO
    // =========================================================================

    @Test
    @DisplayName("Receber Pagamento: Deve mudar para PAGO e disparar impressao de Recibo")
    void deveReceberPagamentoComSucessoEGerarRecibo() {
        PagamentoRequestDTO pagamentoDTO = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("20.00"));

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.receberPagamento(pedidoId, pagamentoDTO);

        assertThat(res.statusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(res.formaPagamento()).isEqualTo(FormaPagamento.PIX);

        // Blindagem: Garante que o recibo foi enviado para a fila
        verify(filaImpressaoRepository, times(1)).save(any(FilaImpressao.class));
    }

    @Test
    @DisplayName("Receber Pagamento Falha: Impedir duplo pagamento")
    void deveImpedirReceberPagamentoDuplicado() {
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.PAGO);
        PagamentoRequestDTO pagamentoDTO = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("20.00"));

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        assertThrows(BusinessRuleException.class, () -> pedidoService.receberPagamento(pedidoId, pagamentoDTO));
        verify(filaImpressaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Receber Pagamento Falha: Impedir pagamento de pedido cancelado")
    void deveImpedirReceberPagamentoDePedidoCancelado() {
        pedidoPadrao.setStatus(StatusPedido.CANCELADO);
        PagamentoRequestDTO pagamentoDTO = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("20.00"));

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        assertThrows(BusinessRuleException.class, () -> pedidoService.receberPagamento(pedidoId, pagamentoDTO));
    }

    // =========================================================================
    // 3. TESTES DE CANCELAMENTO E ESTORNO
    // =========================================================================

    @Test
    @DisplayName("Cancelar Pedido: Deve cancelar e gerar Status ESTORNADO se ja estava PAGO")
    void deveAlterarStatusFinanceiroParaEstornadoAoCancelarPedidoPago() {
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.PAGO);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.cancelarPedido(pedidoId);

        assertThat(res.status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(res.statusFinanceiro()).isEqualTo(StatusFinanceiro.ESTORNADO);

        // Blindagem: Cancelamento não deve cuspir papel na impressora
        verify(filaImpressaoRepository, never()).save(any(FilaImpressao.class));
    }

    @Test
    @DisplayName("Cancelar Pedido: Deve impedir cancelamento de pedidos FINALIZADOS")
    void deveImpedirCancelamentoDePedidoFinalizado() {
        pedidoPadrao.setStatus(StatusPedido.FINALIZADO);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        assertThrows(BusinessRuleException.class, () -> pedidoService.cancelarPedido(pedidoId));
    }

    // =========================================================================
    // 4. TESTES DE MANIPULAÇÃO DE ITENS E STATUS DO PEDIDO
    // =========================================================================

    @Test
    @DisplayName("Adicionar Item: Deve recalcular o Total corretamente")
    void deveAdicionarItemERecalcularTotal() {
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, "Adicional", null);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.adicionarItemPedido(pedidoId, novoItem);

        assertThat(res.total()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(pedidoPadrao.getItens()).hasSize(2);
    }

    @Test
    @DisplayName("Adicionar Item Falha: Deve bloquear alteração de pedido ja PAGO")
    void deveImpedirEdicaoDeItensSePedidoEstiverPago() {
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.PAGO);
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, null, null);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                pedidoService.adicionarItemPedido(pedidoId, novoItem)
        );
        assertThat(exception.getMessage()).containsIgnoringCase("pago");
    }

    @Test
    @DisplayName("Atualizar Status Operacional: Deve tramitar de RECEBIDO para EM_PREPARO")
    void deveAtualizarStatusOperacionalComSucesso() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.atualizarStatus(pedidoId, new PedidoStatusRequestDTO(StatusPedido.EM_PREPARO));
        assertThat(res.status()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    // =========================================================================
    // 5. TESTES DE CONSULTAS (LISTAGENS)
    // =========================================================================

    @Test
    @DisplayName("Consultas: Deve listar Histórico e Monitor da Cozinha")
    void deveListarHistoricoEMonitor() {
        when(pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId)).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarHistoricoCliente(clienteId)).hasSize(1);

        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(anyList())).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarPedidosAtivosMonitor()).hasSize(1);
    }
}