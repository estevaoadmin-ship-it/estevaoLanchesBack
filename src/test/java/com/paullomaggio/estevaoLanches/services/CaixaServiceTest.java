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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaixaServiceTest {

    @Mock private CaixaRepository caixaRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    @Mock private AuditoriaCaixaRepository auditoriaCaixaRepository;
    @Mock private PagamentoRepository pagamentoRepository;

    @InjectMocks
    private CaixaService caixaService;

    private Caixa caixaAberto;
    private Usuario usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Usuario();
        usuarioLogado.setId(UUID.randomUUID());
        usuarioLogado.setNome("Estêvão Dono");
        usuarioLogado.setLogin("estevao");
        usuarioLogado.setRole(RoleUsuario.ADMIN);

        caixaAberto = new Caixa();
        caixaAberto.setId(UUID.randomUUID());
        caixaAberto.setStatus(StatusCaixa.ABERTO);
        caixaAberto.setValorAbertura(new BigDecimal("100.00"));
        caixaAberto.setDataHoraAbertura(LocalDateTime.now());
        caixaAberto.setUsuarioAbertura(usuarioLogado);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(authentication.getPrincipal()).thenReturn(usuarioLogado);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Prevenção de NPE Financeiro Global para testes de movimentação
        lenient().when(movimentacaoCaixaRepository.save(any(MovimentacaoCaixa.class))).thenAnswer(invocation -> {
            MovimentacaoCaixa mc = invocation.getArgument(0);
            if (mc.getId() == null) {
                mc.setId(UUID.randomUUID());
            }
            return mc;
        });
    }

    // =========================================================================
    // 💳 CENÁRIOS DE TESTE: CONTAS FRACIONADAS E DIVISÃO DE PAGAMENTO (NOVOS)
    // =========================================================================

    @Test
    @DisplayName("CENÁRIO A: Pedro paga a sua conta filha e vai embora; pedido mãe continua aberto")
    void cenarioPedroPagaContaFilhaEVaiEmbora() {
        UUID pedidoId = UUID.randomUUID();
        Pedido pedidoMae = criarPedidoParaPedroEJoao(pedidoId);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMae));
        // Pedro (Conta 2) deve R$ 15.00 e ainda não há pagamentos registrados para ele
        when(pagamentoRepository.sumPagamentosPorConta(pedidoId, 2)).thenReturn(BigDecimal.ZERO);

        ContaPagamentoRequestDTO dtoPedro = new ContaPagamentoRequestDTO();
        dtoPedro.setNumeroConta(2); // Conta do Pedro
        dtoPedro.setValorPago(new BigDecimal("15.00"));
        dtoPedro.setFormaPagamento("PIX");

        caixaService.registrarPagamentoFracionado(pedidoId, dtoPedro);

        // Assertivas: O pagamento do Pedro foi salvo, seu item virou PAGO, mas a mesa continua aberta (EM_PREPARO)
        verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
        assertThat(pedidoMae.getItens().get(1).getStatusPagamento()).isEqualTo(StatusPagamento.PAGO); // Item do Pedro
        assertThat(pedidoMae.getItens().get(0).getStatusPagamento()).isEqualTo(StatusPagamento.ABERTO); // Item do João continua aberto
        assertThat(pedidoMae.getStatus()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    @Test
    @DisplayName("CENÁRIO B: João paga a sua conta filha após a saída do Pedro; pedido mãe é FINALIZADO")
    void cenarioJoaoPagaRestanteEFinalizaPedidoMae() {
        UUID pedidoId = UUID.randomUUID();
        Pedido pedidoMae = criarPedidoParaPedroEJoao(pedidoId);

        // Simulando que o Pedro já pagou a dele (Conta 2) anteriormente
        pedidoMae.getItens().get(1).setStatusPagamento(StatusPagamento.PAGO);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMae));
        // João (Conta 1) deve R$ 35.00
        when(pagamentoRepository.sumPagamentosPorConta(pedidoId, 1)).thenReturn(BigDecimal.ZERO);

        ContaPagamentoRequestDTO dtoJoao = new ContaPagamentoRequestDTO();
        dtoJoao.setNumeroConta(1); // Conta do João
        dtoJoao.setValorPago(new BigDecimal("35.00"));
        dtoJoao.setFormaPagamento("DINHEIRO");

        caixaService.registrarPagamentoFracionado(pedidoId, dtoJoao);

        // Assertivas: Itens do João foram quitados e a mesa mãe foi encerrada automaticamente
        assertThat(pedidoMae.getItens().get(0).getStatusPagamento()).isEqualTo(StatusPagamento.PAGO);
        assertThat(pedidoMae.getStatus()).isEqualTo(StatusPedido.FINALIZADO);
        verify(pedidoRepository, times(1)).save(pedidoMae);
    }

    @Test
    @DisplayName("CENÁRIO C: Clientes dividem a mesma conta (Conta 1) em Dinheiro e PIX no caixa")
    void cenarioDividirMesmaContaEntreDinheiroEPix() {
        UUID pedidoId = UUID.randomUUID();
        Pedido pedidoMesa = criarPedidoContaUnica(pedidoId); // Conta 1 totalizando R$ 50.00

        // Criação de um acumulador dinâmico para simular o comportamento real do banco de dados entre as transações
        AtomicReference<BigDecimal> saldoPagoNoBanco = new AtomicReference<>(BigDecimal.ZERO);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMesa));
        when(pagamentoRepository.sumPagamentosPorConta(eq(pedidoId), eq(1)))
                .thenAnswer(invocation -> saldoPagoNoBanco.get());

        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento p = invocation.getArgument(0);
            saldoPagoNoBanco.set(saldoPagoNoBanco.get().add(p.getValorPago()));
            return p;
        });

        // 1. Primeiro amigo paga R$ 20.00 no Dinheiro
        ContaPagamentoRequestDTO pag1 = new ContaPagamentoRequestDTO();
        pag1.setNumeroConta(1);
        pag1.setValorPago(new BigDecimal("20.00"));
        pag1.setFormaPagamento("DINHEIRO");
        caixaService.registrarPagamentoFracionado(pedidoId, pag1);

        assertThat(pedidoMesa.getItens().get(0).getStatusPagamento()).isEqualTo(StatusPagamento.ABERTO);
        assertThat(pedidoMesa.getStatus()).isNotEqualTo(StatusPedido.FINALIZADO);

        // 2. Segundo amigo liquida os R$ 30.00 restantes no PIX
        ContaPagamentoRequestDTO pag2 = new ContaPagamentoRequestDTO();
        pag2.setNumeroConta(1);
        pag2.setValorPago(new BigDecimal("30.00"));
        pag2.setFormaPagamento("PIX");
        caixaService.registrarPagamentoFracionado(pedidoId, pag2);

        // Assertivas: Conta total zerou, os itens foram para PAGO e o pedido fechou
        assertThat(pedidoMesa.getItens().get(0).getStatusPagamento()).isEqualTo(StatusPagamento.PAGO);
        assertThat(pedidoMesa.getStatus()).isEqualTo(StatusPedido.FINALIZADO);
    }

    @Test
    @DisplayName("CENÁRIO D: Deve rejeitar pagamento se o valor digitado for maior do que o saldo devedor")
    void deveRejeitarPagamentoAcimaDoSaldoDaConta() {
        UUID pedidoId = UUID.randomUUID();
        Pedido pedido = criarPedidoContaUnica(pedidoId); // Deve R$ 50.00

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pagamentoRepository.sumPagamentosPorConta(pedidoId, 1)).thenReturn(BigDecimal.ZERO);

        ContaPagamentoRequestDTO dtoInvalido = new ContaPagamentoRequestDTO();
        dtoInvalido.setNumeroConta(1);
        dtoInvalido.setValorPago(new BigDecimal("60.00")); // Passou do valor de 50.00
        dtoInvalido.setFormaPagamento("CREDITO");

        assertThrows(BusinessRuleException.class, () ->
                caixaService.registrarPagamentoFracionado(pedidoId, dtoInvalido)
        );
    }

    // =========================================================================
    // ⚙️ TESTES EXISTENTES DE GESTÃO DE TURNO DO CAIXA (PRESERVADOS E AJUSTADOS)
    // =========================================================================

    @Test
    @DisplayName("CT-CAIXA-001: Deve obter status atual do caixa com sucesso")
    void deveObterStatusAtual() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));

        Optional<CaixaStatusResponseDTO> status = caixaService.obterStatusAtual();

        assertThat(status).isPresent();
        assertThat(status.get().valorAbertura()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("CT-CAIXA-002: Deve calcular resumo do turno perfeitamente utilizando Enums de FormaPagamento")
    void deveObterResumoTurnoComSucesso() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));

        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq(FormaPagamento.DINHEIRO), eq(StatusPedido.FINALIZADO))).thenReturn(new BigDecimal("200.00"));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq(FormaPagamento.PIX), eq(StatusPedido.FINALIZADO))).thenReturn(new BigDecimal("150.00"));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq(FormaPagamento.CREDITO), eq(StatusPedido.FINALIZADO))).thenReturn(new BigDecimal("100.00"));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq(FormaPagamento.DEBITO), eq(StatusPedido.FINALIZADO))).thenReturn(new BigDecimal("50.00"));

        when(movimentacaoCaixaRepository.somarPorCaixaETipo(any(), eq(TipoMovimentacao.SUPRIMENTO))).thenReturn(new BigDecimal("50.00"));
        when(movimentacaoCaixaRepository.somarPorCaixaETipo(any(), eq(TipoMovimentacao.SANGRIA))).thenReturn(new BigDecimal("20.00"));
        when(pedidoRepository.countPedidosAtivos(any(), any())).thenReturn(3L);

        CaixaResumoResponseDTO resumo = caixaService.obterResumoTurno();

        assertThat(resumo.faturamentoTotal()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(resumo.totalEsperadoGaveta()).isEqualByComparingTo(new BigDecimal("330.00"));
        assertThat(resumo.pedidosEmEsteira()).isEqualTo(3L);
    }

    @Test
    @DisplayName("CT-CAIXA-003: Deve lançar erro ao colher resumo se não houver caixa ativo")
    void deveLancarErroNoResumoSemCaixaAtivo() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> caixaService.obterResumoTurno());
    }

    @Test
    @DisplayName("CT-CAIXA-004: Deve abrir caixa com sucesso e gravar histórico")
    void deveAbrirCaixaComSucesso() {
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);
        when(caixaRepository.save(any(Caixa.class))).thenReturn(caixaAberto);

        CaixaAberturaRequestDTO dto = new CaixaAberturaRequestDTO(new BigDecimal("100.00"));
        CaixaStatusResponseDTO response = caixaService.abrirCaixa(dto);

        assertThat(response).isNotNull();
        verify(movimentacaoCaixaRepository, times(1)).save(any(MovimentacaoCaixa.class));
    }

    @Test
    @DisplayName("CT-CAIXA-005: Deve impedir abertura de caixa caso já exista um ativo")
    void deveImpedirAberturaSeJaEstiverAberto() {
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);

        CaixaAberturaRequestDTO dto = new CaixaAberturaRequestDTO(new BigDecimal("100.00"));
        assertThrows(BusinessRuleException.class, () -> caixaService.abrirCaixa(dto));
    }

    @Test
    @DisplayName("CT-CAIXA-006: Deve fechar caixa com sucesso quando valores baterem")
    void deveFecharCaixaComValoresExatos() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(movimentacaoCaixaRepository.somarPorCaixaETipo(any(), any())).thenReturn(BigDecimal.ZERO);

        CaixaFechamentoRequestDTO dto = new CaixaFechamentoRequestDTO(new BigDecimal("100.00"), "");
        caixaService.fecharCaixa(dto);

        assertThat(caixaAberto.getStatus()).isEqualTo(StatusCaixa.FECHADO);
        verify(caixaRepository, times(1)).save(caixaAberto);
    }

    @Test
    @DisplayName("CT-CAIXA-007: Deve exigir justificativa obrigatória em caso de quebra de caixa")
    void deveExigirJustificativaSeHouverDiferenca() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(movimentacaoCaixaRepository.somarPorCaixaETipo(any(), any())).thenReturn(BigDecimal.ZERO);

        CaixaFechamentoRequestDTO dtoSemJustificativa = new CaixaFechamentoRequestDTO(new BigDecimal("90.00"), "   ");

        assertThrows(BusinessRuleException.class, () -> caixaService.fecharCaixa(dtoSemJustificativa));
    }

    @Test
    @DisplayName("CT-CAIXA-010: Deve processar suprimento de caixa com sucesso")
    void deveLancarSuprimentoComSucesso() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));

        MovimentacaoRequestDTO dto = new MovimentacaoRequestDTO(new BigDecimal("50.00"), MotivoMovimentacao.REFORCO_TROCO, "Moedas");
        caixaService.lancarSuprimento(dto);

        verify(movimentacaoCaixaRepository, times(1)).save(any(MovimentacaoCaixa.class));
    }

    // ==========================================
    // 🛠️ MÉTODOS AUXILIARES DE SUPORTE (MOCKS)
    // ==========================================

    private Pedido criarPedidoParaPedroEJoao(UUID pedidoId) {
        Pedido p = new Pedido();
        p.setId(pedidoId);
        p.setStatus(StatusPedido.EM_PREPARO);

        ItemPedido itemJoao = new ItemPedido();
        itemJoao.setNumeroConta(1); // Conta do João
        itemJoao.setPrecoUnitario(new BigDecimal("35.00"));
        itemJoao.setQuantidade(1);
        itemJoao.setStatusPagamento(StatusPagamento.ABERTO);

        ItemPedido itemPedro = new ItemPedido();
        itemPedro.setNumeroConta(2); // Conta do Pedro
        itemPedro.setPrecoUnitario(new BigDecimal("15.00"));
        itemPedro.setQuantidade(1);
        itemPedro.setStatusPagamento(StatusPagamento.ABERTO);

        p.setItens(new ArrayList<>(List.of(itemJoao, itemPedro)));
        return p;
    }

    private Pedido criarPedidoContaUnica(UUID pedidoId) {
        Pedido p = new Pedido();
        p.setId(pedidoId);
        p.setStatus(StatusPedido.EM_PREPARO);

        ItemPedido itemMesa = new ItemPedido();
        itemMesa.setNumeroConta(1); // Todo mundo na mesma conta
        itemMesa.setPrecoUnitario(new BigDecimal("25.00"));
        itemMesa.setQuantidade(2); // Total R$ 50.00
        itemMesa.setStatusPagamento(StatusPagamento.ABERTO);

        p.setItens(new ArrayList<>(List.of(itemMesa)));
        return p;
    }
}