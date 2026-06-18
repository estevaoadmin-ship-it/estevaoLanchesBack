package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        itemPedidoExistente.setNumeroConta(1);
        itemPedidoExistente.setStatusPagamento(StatusPagamento.ABERTO);
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

        verify(filaImpressaoRepository, times(2)).save(any(FilaImpressao.class));
    }

    @Test
    @DisplayName("Checkout Venda Rápida (Balcão): Deve gerar pedido ignorando o carrinho e enviando para impressão")
    void deveCriarRegistroNaFilaAoFinalizarVendaRapida() {
        List<ItemPedidoRequestDTO> itensAvulsos = List.of(new ItemPedidoRequestDTO(prodAId, 1, null, null, 1));
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

        verify(filaImpressaoRepository, times(2)).save(any(FilaImpressao.class));
        verify(carrinhoRepository, never()).findByClienteId(any());
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
        verify(filaImpressaoRepository, times(1)).save(any(FilaImpressao.class));
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
    // 3. TESTES DE CANCELAMENTO E ESTORNO (BLINDAGEM ANTIFANTASMA)
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

        verify(filaImpressaoRepository, never()).save(any(FilaImpressao.class));
    }

    @Test
    @DisplayName("Blindagem Antifantasma: Cancelar pedido abandonado sem pagar deve zerar financeiro como CANCELADO")
    void deveMudarParaCanceladoSeNaoEstavaPagoAoCancelar() {
        pedidoPadrao.setStatus(StatusPedido.PRONTO);
        pedidoPadrao.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.cancelarPedido(pedidoId);

        assertThat(res.status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(res.statusFinanceiro()).isEqualTo(StatusFinanceiro.CANCELADO);
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
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, "Adicional", null, 1);

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
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, null, null, 1);

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
    // 5. TESTES DE CONSULTAS E FILTROS DO MONITOR
    // =========================================================================

    @Test
    @DisplayName("Consultas: Deve listar Histórico do Cliente corretamente")
    void deveListarHistoricoDoCliente() {
        when(pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId)).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarHistoricoCliente(clienteId)).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Blindagem Monitor Cozinha: Deve buscar apenas status de preparo ativos (RECEBIDO, EM_PREPARO, PRONTO)")
    void deveListarPedidosAtivosMonitorFiltrandoApenasEtapasDeProducao() {
        ArgumentCaptor<List<StatusPedido>> statusCaptor = ArgumentCaptor.forClass(List.class);
        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(statusCaptor.capture())).thenReturn(List.of(pedidoPadrao));

        List<PedidoResponseDTO> resultado = pedidoService.listarPedidosAtivosMonitor();

        assertThat(resultado).hasSize(1);

        List<StatusPedido> statusEnviados = statusCaptor.getValue();
        assertThat(statusEnviados).containsExactlyInAnyOrder(
                StatusPedido.RECEBIDO,
                StatusPedido.EM_PREPARO,
                StatusPedido.PRONTO
        );
    }

    // =========================================================================
    // 6. TESTES DE INTEGRAÇÃO E REGRAS DE CONTAS FRACIONADAS
    // =========================================================================

    @Test
    @DisplayName("Contas Fracionadas: Deve permitir criar venda balcão com itens em comandas filhas diferentes")
    void deveCriarPedidoComMultiplasContasNoCheckout() {
        ItemPedidoRequestDTO itemJoao = new ItemPedidoRequestDTO(prodAId, 1, "Para o João", null, 1);
        ItemPedidoRequestDTO itemPedro = new ItemPedidoRequestDTO(prodBId, 1, "Para o Pedro", null, 2);

        List<ItemPedidoRequestDTO> itensAvulsos = List.of(itemJoao, itemPedro);
        CheckoutRequestDTO dto = new CheckoutRequestDTO(
                null, TipoPedido.RETIRADA, null, null, null,
                "Pedro e Joao Balcao", null, FormaPagamento.PIX, new BigDecimal("30.00"), itensAvulsos
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dto);

        assertThat(resultado.statusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Contas Fracionadas: Deve adicionar item direcionando para a comanda filha informada")
    void deveAdicionarItemEmContaEspecificaERecalcularTotalGeral() {
        ItemPedidoRequestDTO novoItemPedro = new ItemPedidoRequestDTO(prodBId, 1, "Adicionar na Conta do Pedro", null, 2);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.adicionarItemPedido(pedidoId, novoItemPedro);

        assertThat(res.total()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(pedidoPadrao.getItens()).hasSize(2);
    }

    @Test
    @DisplayName("Contas Fracionadas: Deve bloquear a adição de lanches caso a comanda filha informada já tenha sido quitada")
    void deveImpedirAdicaoDeItemSeAqueleNumeroDeContaJaEstiverPago() {
        itemPedidoExistente.setNumeroConta(1);
        itemPedidoExistente.setStatusPagamento(StatusPagamento.PAGO);

        ItemPedidoRequestDTO novoItemContaFechada = new ItemPedidoRequestDTO(prodBId, 1, "Burlar conta já paga", null, 1);


        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                pedidoService.adicionarItemPedido(pedidoId, novoItemContaFechada)
        );
        assertThat(exception.getMessage()).containsIgnoringCase("paga");
    }

    @Test
    @DisplayName("Contas Fracionadas: Deve impedir a remoção de um item caso a comanda filha já tenha sido paga")
    void deveImpedirRemocaoDeItemSeContaJaEstiverPago() {
        itemPedidoExistente.setNumeroConta(1);
        itemPedidoExistente.setStatusPagamento(StatusPagamento.PAGO); // Conta já paga

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                pedidoService.removerItemPedido(pedidoId, itemPedidoExistente.getId())
        );
        assertThat(exception.getMessage()).containsIgnoringCase("paga");
    }

    @Test
    @DisplayName("Contas Fracionadas: Deve impedir a atualização de adicionais se a comanda filha já tiver sido paga")
    void deveImpedirAtualizacaoDeAdicionaisSeContaJaEstiverPago() {
        itemPedidoExistente.setNumeroConta(1);
        itemPedidoExistente.setStatusPagamento(StatusPagamento.PAGO); // Conta já paga

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                pedidoService.atualizarAdicionaisDoItem(pedidoId, itemPedidoExistente.getId(), List.of(UUID.randomUUID()))
        );
        assertThat(exception.getMessage()).containsIgnoringCase("paga");
    }
}