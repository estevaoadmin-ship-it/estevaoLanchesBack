package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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
    @Mock private AdicionalRepository adicionalRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PedidoService pedidoService;

    private Cliente cliente;
    private Carrinho carrinho;
    private Pedido pedidoPadrao;
    private ItemPedido itemPedidoExistente;
    private Produto prodA, prodB, prodBebida;
    private UUID clienteId, pedidoId, prodAId, prodBId, prodBebidaId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();
        prodAId = UUID.randomUUID();
        prodBId = UUID.randomUUID();
        prodBebidaId = UUID.randomUUID();

        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("Maria Santos");

        prodA = new Produto(); prodA.setId(prodAId); prodA.setPreco(new BigDecimal("10.00")); prodA.setNome("X-Bacon"); prodA.setPrecisaPreparo(true);
        prodB = new Produto(); prodB.setId(prodBId); prodB.setPreco(new BigDecimal("20.00")); prodB.setNome("X-Tudo"); prodB.setPrecisaPreparo(true);

        prodBebida = new Produto();
        prodBebida.setId(prodBebidaId);
        prodBebida.setPreco(new BigDecimal("6.00"));
        prodBebida.setNome("COCA-COLA LATA");
        prodBebida.setPrecisaPreparo(false);

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
        pedidoPadrao.setTipo(TipoPedido.MESA);
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
    // 1. TESTES DE CHECKOUT DO PDV CENTRAL
    // =========================================================================
    @Test
    @DisplayName("Checkout via Carrinho: Deve esvaziar carrinho e gerar pedido")
    void deveFinalizarCheckoutViaCarrinhoComSucesso() {
        CheckoutRequestDTO dto = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, "Rua A", null, null, null, null, FormaPagamento.CREDITO, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dto);

        assertThat(resultado.statusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
        assertThat(carrinho.getItens()).isEmpty();
        verify(filaImpressaoRepository, times(2)).save(any(FilaImpressao.class));
    }

    // =========================================================================
    // 2. TESTES DE REGRAS DE CONTAS FRACIONADAS (SUBCONTAS)
    // =========================================================================
    @Test
    @DisplayName("Contas Fracionadas: Deve bloquear a adição de lanches caso a subcomanda informada já tenha sido quitada")
    void deveImpedirAdicaoDeItemSeAqueleNumeroDeContaJaEstiverPago() {
        itemPedidoExistente.setNumeroConta(2);
        itemPedidoExistente.setStatusPagamento(StatusPagamento.PAGO);

        ItemPedidoRequestDTO novoItemContaFechada = new ItemPedidoRequestDTO(prodBId, 1, "Burlar conta", null, 2);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        assertThrows(BusinessRuleException.class, () -> pedidoService.adicionarItemPedido(pedidoId, novoItemContaFechada));
        verify(pedidoRepository, never()).save(any());
    }

    // =========================================================================
    // 3. TESTES DO FLUXO MOBILE PURIFICADO (CONVERSÃO ATÔMICA E TRIAGEM)
    // =========================================================================
    @Test
    @DisplayName("Mesa Mobile: Deve usar saveAndFlush para persistência atômica, eliminando erros 400 de concorrência")
    void deveCriarNovoPedidoMesaMobileComSucessoESaveAndFlush() {
        ItemMobileRequestDTO itemDto = new ItemMobileRequestDTO(prodAId, 1, "Sem cebola", null);
        ClienteMobileRequestDTO clienteDto = new ClienteMobileRequestDTO("paulo fernando", null);
        PedidoMobileRequestDTO dto = new PedidoMobileRequestDTO(null, 35, 1, clienteDto, List.of(itemDto));

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));

        // Intercepta a chamada obrigatória ao saveAndFlush da nova arquitetura
        when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.processarPedidoMobile(dto);

        assertThat(res.total()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(res.clienteNome()).isEqualTo("PAULO FERNANDO"); // Higienizado em Caixa Alta

        verify(pedidoRepository, times(1)).saveAndFlush(any(Pedido.class));
        verify(pedidoRepository, never()).save(any(Pedido.class)); // O save tradicional foi abolido aqui
    }

    @Test
    @DisplayName("🆕 NEW: Triagem Inteligente - Se o lote contiver apenas bebidas prontas, NÃO deve gerar cupom para a cozinha")
    void deveIgnorarFilaDaCozinhaSeLotePossuirApenasBebidas() {
        ItemMobileRequestDTO itemDto = new ItemMobileRequestDTO(prodBebidaId, 2, "Bem gelada", null);
        PedidoMobileRequestDTO dto = new PedidoMobileRequestDTO(null, 12, 1, null, List.of(itemDto));

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodBebidaId)).thenReturn(Optional.of(prodBebida));
        when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        pedidoService.processarPedidoMobile(dto);

        // 🎯 ASSERTIVA DE OURO: Deve criar APENAS o cupom do Caixa (RECIBO_CLIENTE). A cozinha não deve ser acionada.
        verify(filaImpressaoRepository, times(1)).save(argThat(fila ->
                fila.getDestino() == FilaImpressao.DestinoImpressao.RECIBO_CLIENTE
        ));
        verify(filaImpressaoRepository, never()).save(argThat(fila ->
                fila.getDestino() == FilaImpressao.DestinoImpressao.COZINHA
        ));
    }

    @Test
    @DisplayName("🆕 NEW: Triagem Inteligente - Se o lote contiver lanches de chapa, DEVE enviar cupom para a cozinha e para o caixa")
    void deveGerarFilaCozinhaECaixaSeLotePossuirLanches() {
        ItemMobileRequestDTO itemDto = new ItemMobileRequestDTO(prodAId, 1, "Ponto da carne", null);
        PedidoMobileRequestDTO dto = new PedidoMobileRequestDTO(null, 14, 1, null, List.of(itemDto));

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        pedidoService.processarPedidoMobile(dto);

        // Deve agendar duas tarefas independentes no pool de hardware
        verify(filaImpressaoRepository, times(1)).save(argThat(f -> f.getDestino() == FilaImpressao.DestinoImpressao.COZINHA));
        verify(filaImpressaoRepository, times(1)).save(argThat(f -> f.getDestino() == FilaImpressao.DestinoImpressao.RECIBO_CLIENTE));
    }

    @Test
    @DisplayName("Mobile WebSockets: Deve certificar o broadcast integrado síncrono")
    void deveGarantirDadosIntegradosNoBroadcastDoWebSocket() {
        ItemMobileRequestDTO itemDto = new ItemMobileRequestDTO(prodAId, 2, null, null);
        PedidoMobileRequestDTO dto = new PedidoMobileRequestDTO(null, 15, 1, null, List.of(itemDto));

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        pedidoService.processarPedidoMobile(dto);

        verify(messagingTemplate).convertAndSend(eq("/topic/caixa"), any(PedidoResponseDTO.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/cozinha"), any(PedidoResponseDTO.class));
    }
}