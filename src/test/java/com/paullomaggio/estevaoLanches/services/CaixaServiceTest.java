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
import java.util.Optional;
import java.util.UUID;

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

    @InjectMocks
    private CaixaService caixaService;

    private Caixa caixaAberto;
    private Usuario usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Usuario();
        usuarioLogado.setId(UUID.randomUUID());
        usuarioLogado.setNome("Estêvão Dono");
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

        // 🚀 PREVENÇÃO DE NPE FINANCEIRO Global
        lenient().when(movimentacaoCaixaRepository.save(any(MovimentacaoCaixa.class))).thenAnswer(invocation -> {
            MovimentacaoCaixa mc = invocation.getArgument(0);
            if (mc.getId() == null) {
                mc.setId(UUID.randomUUID());
            }
            return mc;
        });
    }

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

        // 🚀 CORRIGIDO: Agora espera times(1) de salvamento de movimentação financeira.
        verify(movimentacaoCaixaRepository, times(1)).save(any(MovimentacaoCaixa.class));
    }
}