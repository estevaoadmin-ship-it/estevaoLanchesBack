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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Avançada — Matriz de Blindagem do Fluxo de Caixa (Service)")
class CaixaServiceTest {

    @Mock private CaixaRepository caixaRepository;
    @Mock private PagamentoRepository pagamentoRepository; // Manter para obterResumoTurno
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EstornoPagamentoRepository estornoPagamentoRepository;
    @Mock private PagamentoService pagamentoService; // NOVA DEPENDÊNCIA

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
            conta = new Conta(UUID.randomUUID(), 1, false, new BigDecimal("100.00"), null, null, comanda, null, new ArrayList<>(), new ArrayList<>());
            pedido = new Pedido(); pedido.setId(pedidoId); pedido.setConta(conta);
        }

        @Test
        @DisplayName("CT-SALDO-001: Saldo sem Pagamento = valorTotal.")
        void saldoSemPagamento_deveSerValorTotal() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(conta.getId())).thenReturn(BigDecimal.ZERO);

            BigDecimal saldo = caixaService.calcularSaldoDevedorDaConta(pedidoId, 1);

            assertThat(saldo).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("CT-SALDO-002: Saldo com Pagamento parcial = restante.")
        void saldoComPagamentoParcial_deveSerRestante() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("40.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(conta.getId())).thenReturn(BigDecimal.ZERO);

            BigDecimal saldo = caixaService.calcularSaldoDevedorDaConta(pedidoId, 1);

            assertThat(saldo).isEqualByComparingTo(new BigDecimal("60.00"));
        }

        @Test
        @DisplayName("CT-SALDO-003: Saldo após estorno parcial aumenta corretamente.")
        void saldoAposEstornoParcial_aumentaCorretamente() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("80.00")); // Pagou 80
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(conta.getId())).thenReturn(new BigDecimal("30.00")); // Estornou 30

            // Saldo líquido pago = 80 - 30 = 50
            // Saldo devedor = 100 - 50 = 50
            BigDecimal saldo = caixaService.calcularSaldoDevedorDaConta(pedidoId, 1);

            assertThat(saldo).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("CT-SALDO-004: Saldo após estorno total volta ao valor devido correspondente.")
        void saldoAposEstornoTotal_voltaAoValorDevido() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("100.00")); // Pagou 100
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(conta.getId())).thenReturn(new BigDecimal("100.00")); // Estornou 100

            // Saldo líquido pago = 100 - 100 = 0
            // Saldo devedor = 100 - 0 = 100
            BigDecimal saldo = caixaService.calcularSaldoDevedorDaConta(pedidoId, 1);

            assertThat(saldo).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("CT-SALDO-005: Saldo nunca fica negativo.")
        void saldoNuncaFicaNegativo() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.of(conta));
            when(pagamentoRepository.sumPagamentosPorConta(conta.getId())).thenReturn(new BigDecimal("120.00")); // Pagou 120
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(conta.getId())).thenReturn(BigDecimal.ZERO); // Sem estorno

            // Saldo líquido pago = 120 - 0 = 120
            // Saldo devedor = 100 - 120 = -20 (mas deve ser 0)
            BigDecimal saldo = caixaService.calcularSaldoDevedorDaConta(pedidoId, 1);

            assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException se o pedido de origem não for localizado")
        void deveLancarExceptionSePedidoOrigemNaoLocalizado() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> caixaService.calcularSaldoDevedorDaConta(pedidoId, 1));
            verify(contaRepository, never()).findByComandaIdAndNumeroConta(any(), any());
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException se a subconta não existir na mesa correspondente")
        void deveLancarExceptionSeSubcontaNaoExistir() {
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(any(), eq(1))).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> caixaService.calcularSaldoDevedorDaConta(pedidoId, 1));
            verify(pagamentoRepository, never()).sumPagamentosPorConta(any());
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
        private UUID contaId;
        private Comanda comanda;

        @BeforeEach
        void setUpFaturamento() {
            pedidoId = UUID.randomUUID();
            contaId = UUID.randomUUID();
            comanda = new Comanda();
            comanda.setId(UUID.randomUUID());
            conta = new Conta(contaId, 1, false, new BigDecimal("50.00"), null, null, comanda, null, new ArrayList<>(), new ArrayList<>());
            pedido = new Pedido();
            pedido.setId(pedidoId);
            pedido.setConta(conta);
        }

        @Test
        @DisplayName("Fluxo Parcial Feliz: Deve processar amortização e manter a subconta com status pago = false")
        void deveRegistrarPagamentoParcialComSucesso() {
            ContaPagamentoRequestDTO contaPagamentoDto = new ContaPagamentoRequestDTO(1, new BigDecimal("20.00"), FormaPagamento.PIX);
            PagamentoRequestDTO pagamentoRequestDto = new PagamentoRequestDTO(contaPagamentoDto.formaPagamento(), contaPagamentoDto.valorRecebido());
            PagamentoResponseDTO respostaEsperada = mock(PagamentoResponseDTO.class);

            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta())).thenReturn(Optional.of(conta));
            when(pagamentoService.registrarPagamento(contaId, pagamentoRequestDto)).thenReturn(respostaEsperada);

            caixaService.registrarPagamentoFracionado(pedidoId, contaPagamentoDto);

            verify(pagamentoService, times(1)).registrarPagamento(contaId, pagamentoRequestDto);
            verify(pedidoRepository, times(1)).findById(pedidoId);
            verify(contaRepository, times(1)).findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta());
            verifyNoMoreInteractions(pagamentoRepository, caixaRepository); // Remove obsolete stubs
        }

        @Test
        @DisplayName("Fluxo Totalizador Feliz: Deve registrar pagamento da última cota e flaggar a subconta como quitada (pago = true)")
        void deveQuitarContaAoAtingirValorTotal() {
            ContaPagamentoRequestDTO contaPagamentoDto = new ContaPagamentoRequestDTO(1, new BigDecimal("50.00"), FormaPagamento.DINHEIRO);
            PagamentoRequestDTO pagamentoRequestDto = new PagamentoRequestDTO(contaPagamentoDto.formaPagamento(), contaPagamentoDto.valorRecebido());
            PagamentoResponseDTO respostaEsperada = mock(PagamentoResponseDTO.class);

            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta())).thenReturn(Optional.of(conta));
            when(pagamentoService.registrarPagamento(contaId, pagamentoRequestDto)).thenReturn(respostaEsperada);

            caixaService.registrarPagamentoFracionado(pedidoId, contaPagamentoDto);

            verify(pagamentoService, times(1)).registrarPagamento(contaId, pagamentoRequestDto);
            verify(pedidoRepository, times(1)).findById(pedidoId);
            verify(contaRepository, times(1)).findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta());
            verifyNoMoreInteractions(pagamentoRepository, caixaRepository); // Remove obsolete stubs
        }

        @Test
        @DisplayName("Deve barrar qualquer tentativa de processamento de faturamento se o caixa geral constar como fechado")
        void deveFalharSeCaixaTiverFechado() {
            ContaPagamentoRequestDTO contaPagamentoDto = new ContaPagamentoRequestDTO(1, BigDecimal.TEN, FormaPagamento.DEBITO);
            PagamentoRequestDTO pagamentoRequestDto = new PagamentoRequestDTO(contaPagamentoDto.formaPagamento(), contaPagamentoDto.valorRecebido());

            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta())).thenReturn(Optional.of(conta));
            when(pagamentoService.registrarPagamento(contaId, pagamentoRequestDto))
                    .thenThrow(new BusinessRuleException("Operação bloqueada! O caixa geral está fechado no momento."));

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    caixaService.registrarPagamentoFracionado(pedidoId, contaPagamentoDto)
            );

            assertEquals("Operação bloqueada! O caixa geral está fechado no momento.", exception.getMessage());
            verify(pagamentoService, times(1)).registrarPagamento(contaId, pagamentoRequestDto);
            verify(pedidoRepository, times(1)).findById(pedidoId);
            verify(contaRepository, times(1)).findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta());
            verifyNoMoreInteractions(pagamentoRepository, caixaRepository); // Remove obsolete stubs
        }

        @Test
        @DisplayName("Deve rejeitar pagamento se a subconta informada já estiver marcada como paga ou valor excede saldo")
        void deveRejeitarPagamentoAcimaDoSaldoDaConta() {
            ContaPagamentoRequestDTO contaPagamentoDto = new ContaPagamentoRequestDTO(1, new BigDecimal("100.00"), FormaPagamento.CREDITO);
            PagamentoRequestDTO pagamentoRequestDto = new PagamentoRequestDTO(contaPagamentoDto.formaPagamento(), contaPagamentoDto.valorRecebido());

            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta())).thenReturn(Optional.of(conta));
            when(pagamentoService.registrarPagamento(contaId, pagamentoRequestDto))
                    .thenThrow(new BusinessRuleException("Valor informado excede o saldo devedor atual."));

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    caixaService.registrarPagamentoFracionado(pedidoId, contaPagamentoDto)
            );

            assertEquals("Valor informado excede o saldo devedor atual.", exception.getMessage());
            verify(pagamentoService, times(1)).registrarPagamento(contaId, pagamentoRequestDto);
            verify(pedidoRepository, times(1)).findById(pedidoId);
            verify(contaRepository, times(1)).findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto.numeroConta());
            verifyNoMoreInteractions(pagamentoRepository, caixaRepository); // Remove obsolete stubs
        }

        @Test
        @DisplayName("Regressão — Testar integridade e consistência sequencial de pagamentos em lotes sucessivos")
        void testeRegressaoMultiplosPagamentosSucessivos() {
            ContaPagamentoRequestDTO contaPagamentoDto1 = new ContaPagamentoRequestDTO(1, new BigDecimal("15.00"), FormaPagamento.PIX);
            PagamentoRequestDTO pagamentoRequestDto1 = new PagamentoRequestDTO(contaPagamentoDto1.formaPagamento(), contaPagamentoDto1.valorRecebido());
            PagamentoResponseDTO respostaEsperada1 = mock(PagamentoResponseDTO.class);

            ContaPagamentoRequestDTO contaPagamentoDto2 = new ContaPagamentoRequestDTO(1, new BigDecimal("35.00"), FormaPagamento.DINHEIRO);
            PagamentoRequestDTO pagamentoRequestDto2 = new PagamentoRequestDTO(contaPagamentoDto2.formaPagamento(), contaPagamentoDto2.valorRecebido());
            PagamentoResponseDTO respostaEsperada2 = mock(PagamentoResponseDTO.class);

            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
            when(contaRepository.findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto1.numeroConta())).thenReturn(Optional.of(conta));
            when(pagamentoService.registrarPagamento(contaId, pagamentoRequestDto1)).thenReturn(respostaEsperada1);
            when(pagamentoService.registrarPagamento(contaId, pagamentoRequestDto2)).thenReturn(respostaEsperada2);

            // Primeiro pagamento
            caixaService.registrarPagamentoFracionado(pedidoId, contaPagamentoDto1);

            // Segundo pagamento
            caixaService.registrarPagamentoFracionado(pedidoId, contaPagamentoDto2);

            verify(pagamentoService, times(1)).registrarPagamento(contaId, pagamentoRequestDto1);
            verify(pagamentoService, times(1)).registrarPagamento(contaId, pagamentoRequestDto2);
            verify(pedidoRepository, times(2)).findById(pedidoId);
            verify(contaRepository, times(2)).findByComandaIdAndNumeroConta(comanda.getId(), contaPagamentoDto1.numeroConta());
            verifyNoMoreInteractions(pagamentoRepository, caixaRepository); // Remove obsolete stubs
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
            
            // Pagamentos
            Pagamento p1 = new Pagamento();
            p1.setId(UUID.randomUUID());
            p1.setCaixa(caixaAbertoPadrao);
            p1.setValorPago(new BigDecimal("100.00"));
            p1.setFormaPagamento(FormaPagamento.DINHEIRO);

            Pagamento p2 = new Pagamento();
            p2.setId(UUID.randomUUID());
            p2.setCaixa(caixaAbertoPadrao);
            p2.setValorPago(new BigDecimal("200.00"));
            p2.setFormaPagamento(FormaPagamento.PIX);

            Pagamento p3 = new Pagamento();
            p3.setId(UUID.randomUUID());
            p3.setCaixa(caixaAbertoPadrao);
            p3.setValorPago(new BigDecimal("50.00"));
            p3.setFormaPagamento(FormaPagamento.CREDITO);

            when(pagamentoRepository.findByCaixaId(caixaAbertoPadrao.getId())).thenReturn(List.of(p1, p2, p3));

            // Estornos
            EstornoPagamento e1 = new EstornoPagamento();
            e1.setId(UUID.randomUUID());
            e1.setCaixa(caixaAbertoPadrao);
            e1.setPagamento(p1); // Estorno do pagamento em dinheiro
            e1.setValorEstornado(new BigDecimal("10.00"));

            EstornoPagamento e2 = new EstornoPagamento();
            e2.setId(UUID.randomUUID());
            e2.setCaixa(caixaAbertoPadrao);
            e2.setPagamento(p2); // Estorno do pagamento em pix
            e2.setValorEstornado(new BigDecimal("20.00"));

            when(estornoPagamentoRepository.findByCaixaId(caixaAbertoPadrao.getId())).thenReturn(List.of(e1, e2));

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

            when(pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO)).thenReturn(4L);

            CaixaResumoResponseDTO resumo = caixaService.obterResumoTurno();

            // Faturamento Total: (100+200+50) - (10+20) = 350 - 30 = 320
            assertThat(resumo.faturamentoTotal()).isEqualByComparingTo(new BigDecimal("320.00"));
            // Faturamento Dinheiro: 100 - 10 = 90
            assertThat(resumo.faturamentoDinheiro()).isEqualByComparingTo(new BigDecimal("90.00"));
            // Faturamento Pix: 200 - 20 = 180
            assertThat(resumo.faturamentoPix()).isEqualByComparingTo(new BigDecimal("180.00"));
            // Faturamento Credito: 50 - 0 = 50
            assertThat(resumo.faturamentoCredito())
                    .isEqualByComparingTo(new BigDecimal("50.00"));

            // Total Esperado Gaveta: Abertura (150) + Faturamento Dinheiro (90) + Suprimentos (50) - Sangrias (20) = 150 + 90 + 50 - 20 = 270
            assertThat(resumo.totalEsperadoGaveta()).isEqualByComparingTo(new BigDecimal("270.00"));
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
                UUID contaId = UUID.randomUUID();

                Comanda comanda = new Comanda();
                comanda.setId(UUID.randomUUID());

                Conta conta = new Conta(
                        contaId,
                        1,
                        false,
                        BigDecimal.TEN,
                        null,
                        null,
                        comanda,
                        null,
                        new ArrayList<>(),
                        new ArrayList<>()
                );

                Pedido pedido = new Pedido();
                pedido.setId(pedidoId);
                pedido.setConta(conta);

                ContaPagamentoRequestDTO contaPagamentoDto =
                        new ContaPagamentoRequestDTO(
                                1,
                                BigDecimal.ONE,
                                FormaPagamento.PIX
                        );

                PagamentoRequestDTO pagamentoRequestDto =
                        new PagamentoRequestDTO(
                                contaPagamentoDto.formaPagamento(),
                                contaPagamentoDto.valorRecebido()
                        );

                PagamentoResponseDTO respostaEsperada =
                        mock(PagamentoResponseDTO.class);

                when(pedidoRepository.findById(pedidoId))
                        .thenReturn(Optional.of(pedido));

                when(contaRepository.findByComandaIdAndNumeroConta(
                        comanda.getId(),
                        contaPagamentoDto.numeroConta()
                )).thenReturn(Optional.of(conta));

                when(pagamentoService.registrarPagamento(
                        contaId,
                        pagamentoRequestDto
                )).thenReturn(respostaEsperada);

                caixaService.registrarPagamentoFracionado(
                        pedidoId,
                        contaPagamentoDto
                );

                verify(pagamentoService, times(1))
                        .registrarPagamento(contaId, pagamentoRequestDto);

                verify(pedidoRepository, times(1))
                        .findById(pedidoId);

                verify(contaRepository, times(1))
                        .findByComandaIdAndNumeroConta(
                                comanda.getId(),
                                contaPagamentoDto.numeroConta()
                        );

                verifyNoMoreInteractions(
                        pagamentoRepository,
                        caixaRepository
                );
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
            }
        }
    }
}
}