package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Avançada — Matriz de Blindagem do Fluxo de Caixa (Service)")
class CaixaServiceTest {

    @Mock private CaixaRepository caixaRepository;
    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private CaixaService caixaService;

    private Usuario usuarioPadrao;
    private Caixa caixaAbertoPadrao;
    private UUID caixaId;

    @BeforeEach
    void setUp() {
        caixaId = UUID.randomUUID();

        usuarioPadrao = new Usuario();
        usuarioPadrao.setId(UUID.randomUUID());
        usuarioPadrao.setNome("ESTEVAO ADMINISTRADOR");
        usuarioPadrao.setEmail("admin@estevaolanches.com");

        caixaAbertoPadrao = new Caixa(
                caixaId,
                LocalDateTime.now().minusHours(2),
                null,
                StatusCaixa.ABERTO,
                new BigDecimal("150.00"),
                null,
                null,
                null,
                usuarioPadrao,
                null
        );
    }

    // =========================================================================
    // 1. MATRIZ — OBTER STATUS ATUAL
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — obterStatusAtual()")
    class ObterStatusAtualTests {

        @Test
        @DisplayName("Deve retornar o DTO mapeado do caixa aberto ativo no salão")
        void deveRetornarCaixaAberto() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));

            Optional<CaixaStatusResponseDTO> resultado = caixaService.obterStatusAtual();

            assertThat(resultado).isPresent();
            assertThat(resultado.get().status()).isEqualTo(StatusCaixa.ABERTO);
            assertThat(resultado.get().valorAbertura()).isEqualByComparingTo(new BigDecimal("150.00"));
            verify(caixaRepository, times(1)).findByStatus(StatusCaixa.ABERTO);
        }

        @Test
        @DisplayName("Deve retornar Optional.empty() quando não existir nenhum turno aberto")
        void deveRetornarOptionalEmptyQuandoNaoHouverCaixaAberto() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            Optional<CaixaStatusResponseDTO> resultado = caixaService.obterStatusAtual();

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("Garantia de Isolamento: Consulta de status nunca deve realizar escritas ou deleções")
        void consultaNuncaDeveRealizarModificacoes() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));

            caixaService.obterStatusAtual();

            verify(caixaRepository, never()).save(any());
            verify(caixaRepository, never()).delete(any());
        }
    }

    // =========================================================================
    // 2. MATRIZ — ABRIR CAIXA
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — abrirCaixa()")
    class AbrirCaixaTests {

        @Test
        @DisplayName("Deve abrir um novo turno de caixa com sucesso gravando estado inicial correto")
        void deveAbrirNovoCaixaComSucesso() {
            CaixaAberturaRequestDTO dto = new CaixaAberturaRequestDTO(new BigDecimal("200.00"));
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);
            when(usuarioRepository.findAll()).thenReturn(List.of(usuarioPadrao));
            when(caixaRepository.save(any(Caixa.class))).thenAnswer(i -> {
                Caixa c = i.getArgument(0);
                c.setId(caixaId);
                return c;
            });

            CaixaStatusResponseDTO resultado = caixaService.abrirCaixa(dto);

            assertThat(resultado).isNotNull();
            assertThat(resultado.status()).isEqualTo(StatusCaixa.ABERTO);
            assertThat(resultado.valorAbertura()).isEqualByComparingTo(new BigDecimal("200.00"));
            verify(caixaRepository, times(1)).save(any(Caixa.class));
        }

        @Test
        @DisplayName("Deve barrar e lançar BusinessRuleException caso já conste um caixa ativo no sistema")
        void deveLancarExcecaoSeJaHouverCaixaAberto() {
            CaixaAberturaRequestDTO dto = new CaixaAberturaRequestDTO(new BigDecimal("200.00"));
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> caixaService.abrirCaixa(dto));
            verify(caixaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve criar e salvar automaticamente usuário padrão se a tabela de usuários estiver vazia")
        void deveCriarUsuarioPadraoCasoNaoExistaNenhum() {
            CaixaAberturaRequestDTO dto = new CaixaAberturaRequestDTO(new BigDecimal("100.00"));
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);
            when(usuarioRepository.findAll()).thenReturn(Collections.emptyList());
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioPadrao);
            when(caixaRepository.save(any(Caixa.class))).thenReturn(caixaAbertoPadrao);

            caixaService.abrirCaixa(dto);

            verify(usuarioRepository, times(1)).save(any(Usuario.class));
            verify(caixaRepository, times(1)).save(any(Caixa.class));
        }
    }

    // =========================================================================
    // 3. MATRIZ — FECHAR CAIXA
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — fecharCaixa()")
    class FecharCaixaTests {

        @Test
        @DisplayName("Deve localizar turno aberto, aplicar dados de fechamento cego e salvar uma vez")
        void deveFecharCaixaComSucesso() {
            CaixaFechamentoRequestDTO dto = new CaixaFechamentoRequestDTO(new BigDecimal("1200.00"), "Tudo em ordem");
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));
            when(usuarioRepository.findAll()).thenReturn(List.of(usuarioPadrao));
            when(caixaRepository.save(any(Caixa.class))).thenReturn(caixaAbertoPadrao);

            caixaService.fecharCaixa(dto);

            assertThat(caixaAbertoPadrao.getStatus()).isEqualTo(StatusCaixa.FECHADO);
            assertThat(caixaAbertoPadrao.getValorFechamento()).isEqualByComparingTo(new BigDecimal("1200.00"));
            assertThat(caixaAbertoPadrao.getJustificativaDiferenca()).isEqualTo("Tudo em ordem");
            assertThat(caixaAbertoPadrao.getDataHoraFechamento()).isNotNull();
            verify(caixaRepository, times(1)).save(caixaAbertoPadrao);
        }

        @Test
        @DisplayName("Deve estourar BusinessRuleException se tentar fechar turno inexistente ou já encerrado")
        void deveFalharSeNaoHouverCaixaAberto() {
            CaixaFechamentoRequestDTO dto = new CaixaFechamentoRequestDTO(BigDecimal.TEN, "Erro");
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            assertThrows(BusinessRuleException.class, () -> caixaService.fecharCaixa(dto));
            verify(caixaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 4 & 5. MATRIZ — LANÇAR SANGRIA E SUPRIMENTO
    // =========================================================================
    @Nested
    @DisplayName("4 & 5. Camada de Blindagem — Movimentações (Sangria e Suprimento)")
    class MovimentacoesTests {

        @Test
        @DisplayName("Sangria: Deve instanciar registro de fluxo de saída higienizando descrição em caixa alta")
        void deveLancarSangriaComSucesso() {
            MovimentacaoRequestDTO dto = new MovimentacaoRequestDTO(new BigDecimal("50.00"), "  retirada sangria sangria  ");
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));

            caixaService.lancarSangria(dto);

            verify(movimentacaoCaixaRepository, times(1)).save(argThat(mov ->
                    mov.getTipo() == TipoMovimentacao.SANGRIA &&
                            mov.getValor().compareTo(new BigDecimal("50.00")) == 0 &&
                            mov.getDescricao().equals("RETIRADA SANGRIA SANGRIA") &&
                            mov.getCaixa().equals(caixaAbertoPadrao)
            ));
        }

        @Test
        @DisplayName("Suprimento: Deve instanciar registro de aporte de troco higienizando descrição com trim()")
        void deveLancarSuprimentoComSucesso() {
            MovimentacaoRequestDTO dto = new MovimentacaoRequestDTO(new BigDecimal("30.00"), " aporte inicial de troco ");
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));

            caixaService.lancarSuprimento(dto);

            verify(movimentacaoCaixaRepository, times(1)).save(argThat(mov ->
                    mov.getTipo() == TipoMovimentacao.SUPRIMENTO &&
                            mov.getValor().compareTo(new BigDecimal("30.00")) == 0 &&
                            mov.getDescricao().equals("APORTE INICIAL DE TROCO")
            ));
        }

        @Test
        @DisplayName("Deve bloquear movimentações (Sangria/Suprimento) se o caixa geral do sistema constar como fechado")
        void deveBloquearMovimentacaoSeCaixaFechado() {
            MovimentacaoRequestDTO dto = new MovimentacaoRequestDTO(BigDecimal.TEN, "Erro");
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            assertThrows(BusinessRuleException.class, () -> caixaService.lancarSangria(dto));
            assertThrows(BusinessRuleException.class, () -> caixaService.lancarSuprimento(dto));
            verify(movimentacaoCaixaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 6. MATRIZ — ESTORNAR MOVIMENTAÇÃO
    // =========================================================================
    @Nested
    @DisplayName("6. Camada de Blindagem — estornarMovimentacao()")
    class EstornarMovimentacaoTests {

        private MovimentacaoCaixa mov;
        private UUID movId;

        @BeforeEach
        void setUpMov() {
            movId = UUID.randomUUID();
            mov = new MovimentacaoCaixa();
            mov.setId(movId);
            mov.setValor(BigDecimal.TEN);
            mov.setEstornada(false);
        }

        @Test
        @DisplayName("Deve marcar movimentação como estornada salvando a justificativa auditada em UPPERCASE")
        void deveEstornarMovimentacaoComSucesso() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));
            when(movimentacaoCaixaRepository.findById(movId)).thenReturn(Optional.of(mov));

            caixaService.estornarMovimentacao(movId, "  erro de digitação  ");

            assertThat(mov.getEstornada()).isTrue();
            assertThat(mov.getMotivoEstorno()).isEqualTo("ERRO DE DIGITAÇÃO");
            verify(movimentacaoCaixaRepository, times(1)).save(mov);
        }

        @Test
        @DisplayName("Motivo nulo deve acionar fallback automático para string padrão de auditoria do sistema")
        void deveUsarMensagemPadraoSeMotivoForNulo() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));
            when(movimentacaoCaixaRepository.findById(movId)).thenReturn(Optional.of(mov));

            caixaService.estornarMovimentacao(movId, null);

            assertThat(mov.getMotivoEstorno()).isEqualTo("ESTORNO SEM JUSTIFICATIVA EXTRA");
        }

        @Test
        @DisplayName("Deve barrar e reter operação caso a movimentação já conste como estornada")
        void deveLancarExceptionSeJaEstornada() {
            mov.setEstornada(true);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));
            when(movimentacaoCaixaRepository.findById(movId)).thenReturn(Optional.of(mov));

            assertThrows(BusinessRuleException.class, () -> caixaService.estornarMovimentacao(movId, "Tenta de novo"));
            verify(movimentacaoCaixaRepository, never()).save(mov);
        }
    }

    // =========================================================================
    // 7. MATRIZ — REABRIR CAIXA
    // =========================================================================
    @Nested
    @DisplayName("7. Camada de Blindagem — reabrirCaixa()")
    class ReabrirCaixaTests {

        private Caixa caixaFechado;

        @BeforeEach
        void setUpFechado() {
            caixaFechado = new Caixa(caixaId, LocalDateTime.now().minusDays(1), LocalDateTime.now(), StatusCaixa.FECHADO, BigDecimal.TEN, BigDecimal.TEN, "OK", null, usuarioPadrao, usuarioPadrao);
        }

        @Test
        @DisplayName("Deve reabrir turno antigo expurgando tokens de fechamento e setando auditoria de retaguarda")
        void deveReabrirCaixaComSucesso() {
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);
            when(caixaRepository.findById(caixaId)).thenReturn(Optional.of(caixaFechado));

            caixaService.reabrirCaixa(caixaId, "  auditoria solicitou  ");

            assertThat(caixaFechado.getStatus()).isEqualTo(StatusCaixa.ABERTO);
            assertThat(caixaFechado.getDataHoraFechamento()).isNull();
            assertThat(caixaFechado.getValorFechamento()).isNull();
            assertThat(caixaFechado.getUsuarioFechamento()).isNull();
            assertThat(caixaFechado.getMotivoReabertura()).isEqualTo("AUDITORIA SOLICITOU");
            verify(caixaRepository, times(1)).save(caixaFechado);
        }

        @Test
        @DisplayName("Deve explodir BusinessRuleException se houver qualquer outro turno ativo rodando no salão")
        void deveFalharSeHouverOutroCaixaAtivoAberto() {
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> caixaService.reabrirCaixa(caixaId, "Reabrir"));
            verify(caixaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 8. MATRIZ — CALCULAR SALDO DEVEDOR DA CONTA
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — calcularSaldoDevedorDaConta()")
    class CalcularSaldoDevedorDaContaTests {

        private Pedido pedido;
        private Conta conta;
        private UUID pedidoId;

        @BeforeEach
        void setUpConta() {
            pedidoId = UUID.randomUUID();
            Comanda comanda = new Comanda(); comanda.setId(UUID.randomUUID());
            conta = new Conta(UUID.randomUUID(), 1, false, new BigDecimal("100.00"), comanda, null, new ArrayList<>(), new ArrayList<>());
            pedido = new Pedido(); pedido.setId(pedidoId); pedido.setConta(conta);
        }

        @Test
        @DisplayName("Deve abater amortizações e calcular o saldo devedor restante com precisão matemática")
        void deveCalcularSaldoParcial() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("40.00"));

            BigDecimal saldo = caixaService.calcularSaldoDevedorDaConta(pedidoId, 1);

            assertThat(saldo).isEqualByComparingTo(new BigDecimal("60.00"));
        }

        @Test
        @DisplayName("Garantia de Não-Negativo: Se o pagamento for superior ao valor total da conta, o saldo deve ser retido em zero")
        void deveRetornarZeroSePagamentosSuperaremValorDaConta() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("120.00"));

            BigDecimal saldo = caixaService.calcularSaldoDevedorDaConta(pedidoId, 1);

            assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // =========================================================================
    // 9. MATRIZ — REGISTRAR PAGAMENTO FRACIONADO (O PONTO MAIS CRÍTICO)
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — registrarPagamentoFracionado()")
    class RegistrarPagamentoFracionadoTests {

        private Pedido pedido;
        private Conta conta;
        private UUID pedidoId;

        @BeforeEach
        void setUpFaturamento() {
            pedidoId = UUID.randomUUID();
            Comanda comanda = new Comanda(); comanda.setId(UUID.randomUUID());
            conta = new Conta(UUID.randomUUID(), 1, false, new BigDecimal("50.00"), comanda, null, new ArrayList<>(), new ArrayList<>());
            pedido = new Pedido(); pedido.setId(pedidoId); pedido.setConta(conta);
        }

        @Test
        @DisplayName("Fluxo Parcial Feliz: Deve processar amortização e manter a subconta com status pago = false")
        void deveRegistrarPagamentoParcialComSucesso() {
            ContaPagamentoRequestDTO dto = new ContaPagamentoRequestDTO(1, new BigDecimal("20.00"), FormaPagamento.PIX);
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("20.00"));

            caixaService.registrarPagamentoFracionado(pedidoId, dto);

            assertThat(conta.getPago()).isFalse(); // Ainda restam 30 reais
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
            verify(contaRepository, never()).save(conta);
        }

        @Test
        @DisplayName("Fluxo Totalizador Feliz: Deve registrar pagamento da última cota e flaggar a subconta como quitada (pago = true)")
        void deveQuitarContaAoAtingirValorTotal() {
            ContaPagamentoRequestDTO dto = new ContaPagamentoRequestDTO(1, new BigDecimal("30.00"), FormaPagamento.DINHEIRO);
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            // Simula que com este pagamento de 30 reais, a soma histórica atingiu os 50 reais totais da conta
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("50.00"));

            caixaService.registrarPagamentoFracionado(pedidoId, dto);

            assertThat(conta.getPago()).isTrue(); // Totalmente quitada
            verify(contaRepository, times(1)).save(conta);
        }

        @Test
        @DisplayName("Deve barrar qualquer tentativa de processamento de faturamento se o caixa geral constar como fechado")
        void deveFalharSeCaixaTiverFechado() {
            ContaPagamentoRequestDTO dto = new ContaPagamentoRequestDTO(1, BigDecimal.TEN, FormaPagamento.DEBITO);
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);

            assertThrows(BusinessRuleException.class, () -> caixaService.registrarPagamentoFracionado(pedidoId, dto));
        }

        @Test
        @DisplayName("CENÁRIO D (ORIGINAL): Deve rejeitar pagamento se a subconta informada já estiver marcada como paga")
        void deveRejeitarPagamentoAcimaDoSaldoDaConta() {
            Comanda comanda = new Comanda(); comanda.setId(UUID.randomUUID());

            // 🎯 TRAVA DE SEGURANÇA MANTIDA: Configura a conta como PAGO = TRUE de partida
            Conta contaJaPaga = new Conta(UUID.randomUUID(), 1, true, new BigDecimal("50.00"), comanda, null, new ArrayList<>(), new ArrayList<>());
            pedido.setConta(contaJaPaga);

            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(UUID.class), eq(1))).thenReturn(Optional.of(contaJaPaga));

            ContaPagamentoRequestDTO dtoInvalido = new ContaPagamentoRequestDTO(1, new BigDecimal("10.00"), FormaPagamento.CREDITO);

            assertThrows(BusinessRuleException.class, () ->
                    caixaService.registrarPagamentoFracionado(pedidoId, dtoInvalido)
            );
            verify(pagamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Regressão — Testar integridade e consistência sequencial de pagamentos em lotes sucessivos")
        void testeRegressaoMultiplosPagamentosSucessivos() {
            when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));

            // Primeiro Drop de Amortização
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("15.00"));
            caixaService.registrarPagamentoFracionado(pedidoId, new ContaPagamentoRequestDTO(1, new BigDecimal("15.00"), FormaPagamento.PIX));
            assertThat(conta.getPago()).isFalse();

            // Segundo Drop de Amortização que estoura a meta
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("50.01"));
            caixaService.registrarPagamentoFracionado(pedidoId, new ContaPagamentoRequestDTO(1, new BigDecimal("35.01"), FormaPagamento.DINHEIRO));

            assertThat(conta.getPago()).isTrue();
            verify(contaRepository, times(1)).save(conta);
        }
    }

    // =========================================================================
    // 10. MATRIZ — OBTER RESUMO DO TURNO
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — obterResumoTurno() (Prova Real Financeira)")
    class ObterResumoTurnoTests {

        @Test
        @DisplayName("Deve computar e somar faturamentos segregados por canais e bater a prova real exata da gaveta de dinheiro")
        void deveCalcularResumoTurnoCorretamente() {
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));

            // Configura pagamentos cronológicos cruzando a linha do tempo após a abertura do caixa atual
            Pagamento p1 = new Pagamento(UUID.randomUUID(), null, new BigDecimal("100.00"), FormaPagamento.DINHEIRO, LocalDateTime.now(), "OP");
            Pagamento p2 = new Pagamento(UUID.randomUUID(), null, new BigDecimal("200.00"), FormaPagamento.PIX, LocalDateTime.now(), "OP");
            Pagamento p3 = new Pagamento(UUID.randomUUID(), null, new BigDecimal("50.00"), FormaPagamento.CREDITO, LocalDateTime.now(), "OP");
            when(pagamentoRepository.findAll()).thenReturn(List.of(p1, p2, p3));

            // 🎯 FIX DEFINITIVO: Instanciação por setters para evitar acoplamento com construtores ausentes
            MovimentacaoCaixa m1 = new MovimentacaoCaixa();
            m1.setId(UUID.randomUUID());
            m1.setTipo(TipoMovimentacao.SUPRIMENTO);
            m1.setValor(new BigDecimal("50.00"));
            m1.setDescricao("TROCO");
            m1.setEstornada(false);
            m1.setCaixa(caixaAbertoPadrao);

            MovimentacaoCaixa m2 = new MovimentacaoCaixa();
            m2.setId(UUID.randomUUID());
            m2.setTipo(TipoMovimentacao.SANGRIA);
            m2.setValor(new BigDecimal("20.00"));
            m2.setDescricao("ALMOÇO");
            m2.setEstornada(false);
            m2.setCaixa(caixaAbertoPadrao);

            when(movimentacaoCaixaRepository.findByCaixaIdAndEstornadaFalse(caixaId)).thenReturn(List.of(m1, m2));

            // Configura contador da esteira operacional da cozinha (Ignorando finalizados/cancelados)
            when(pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO)).thenReturn(4L);

            CaixaResumoResponseDTO resumo = caixaService.obterResumoTurno();

            // Provas Reais Totais
            assertThat(resumo.faturamentoTotal()).isEqualByComparingTo(new BigDecimal("350.00"));
            assertThat(resumo.faturamentoDinheiro()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(resumo.faturamentoPix()).isEqualByComparingTo(new BigDecimal("200.00"));
            assertThat(resumo.faturamentoCredito()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(resumo.faturamentoDebito()).isEqualByComparingTo(BigDecimal.ZERO);

            // Fórmula Atômica de Auditoria: Abertura (150) + Dinheiro (100) + Suprimentos (50) - Sangrias (20) = 280.00
            assertThat(resumo.totalEsperadoGaveta()).isEqualByComparingTo(new BigDecimal("280.00"));
            assertThat(resumo.pedidosEmEsteira()).isEqualTo(4L);
        }

    // =========================================================================
    // 13. MATRIZ — SIMULAÇÃO DETERMINÍSTICA DE CONCORRÊNCIA EM PDV
    // =========================================================================
    @Nested
    @DisplayName("13. Camada de Blindagem — Testes de Concorrência Simulada")
    class ConcorrenciaSimuladaTests {


            @Test
            @DisplayName("Concorrência — Bloqueio de faturamento enquanto outra estação dispara o fechamento geral do caixa")
            void simulacaoFaturamentoSobreFechamento() {
                UUID pedidoId = UUID.randomUUID();
                ContaPagamentoRequestDTO dto = new ContaPagamentoRequestDTO(1, BigDecimal.ONE, FormaPagamento.PIX);

                when(caixaRepository.existsByStatus(StatusCaixa.ABERTO))
                        .thenReturn(true)
                        .thenReturn(false);

                Pedido pedido = new Pedido();
                Comanda comanda = new Comanda(); comanda.setId(UUID.randomUUID());
                Conta conta = new Conta(UUID.randomUUID(), 1, false, BigDecimal.TEN, comanda, null, new ArrayList<>(), new ArrayList<>());
                pedido.setConta(conta);

                when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
                when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));

                // 🎯 FIX DEFINITIVO: Evita NullPointerException ao simular o retorno da soma histórica de amortizações
                when(pagamentoRepository.sumPagamentosPorConta(any())).thenReturn(BigDecimal.ONE);

                // Execução do faturamento simultâneo
                caixaService.registrarPagamentoFracionado(pedidoId, dto);

                verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
            }
        }

    // =========================================================================
    // 14 & 15. MATRIZ — AUDITORIA ATÓMICA E INTEGRALIDADE DE REGRESSÃO
    // =========================================================================
    @Nested
    @DisplayName("14 & 15. Camada de Blindagem — Auditoria de Metadados e Linha do Tempo")
    class AuditoriaERegressaoIntegradaTests {

        @Test
        @DisplayName("Auditoria — Fluxo de fechamento deve registrar rigorosamente a autoria das modificações para conformidade fiscal")
        void devePreservarUsuariosETrilhasDeAuditoria() {
            CaixaFechamentoRequestDTO dto = new CaixaFechamentoRequestDTO(BigDecimal.ONE, "Auditoria 15");
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoPadrao));
            when(usuarioRepository.findAll()).thenReturn(List.of(usuarioPadrao));

            caixaService.fecharCaixa(dto);

            assertThat(caixaAbertoPadrao.getUsuarioAbertura().getNome()).isEqualTo("ESTEVAO ADMINISTRADOR");
            assertThat(caixaAbertoPadrao.getUsuarioFechamento().getNome()).isEqualTo("ESTEVAO ADMINISTRADOR");
            assertThat(caixaAbertoPadrao.getJustificativaDiferenca()).isEqualTo("Auditoria 15");
        }}}}

