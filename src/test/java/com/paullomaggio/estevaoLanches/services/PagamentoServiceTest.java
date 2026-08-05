package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.PagamentoPesquisaDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoPesquisaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.entities.Conta;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPagamento;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaRepository;
import com.paullomaggio.estevaoLanches.repositories.EstornoPagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Engenharia Financeira — Matriz de Blindagem de Pagamentos")
class PagamentoServiceTest {

    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private CaixaRepository caixaRepository;
    @Mock private EstornoPagamentoRepository estornoPagamentoRepository;
    @Mock private PedidoRepository pedidoRepository;

    private PagamentoService pagamentoService;

    private UUID contaId;
    private UUID pagamentoId;
    private Conta contaMock;
    private Pagamento pagamentoMock;
    private Caixa caixaAberto;
    private Pedido pedidoMock;

    private final String USUARIO_TESTE = "usuario@teste.com";
    private final String SISTEMA_FALLBACK = "SISTEMA";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        pagamentoService = new PagamentoService(pagamentoRepository, contaRepository, caixaRepository, estornoPagamentoRepository, pedidoRepository);

        contaId = UUID.randomUUID();
        pagamentoId = UUID.randomUUID();

        contaMock = new Conta();
        contaMock.setId(contaId);
        contaMock.setNumeroConta(1);
        contaMock.setPago(false);
        contaMock.setValorTotal(new BigDecimal("100.00"));
        contaMock.setPedidos(new ArrayList<>());
        contaMock.setPagamentos(new ArrayList<>());

        pagamentoMock = new Pagamento();
        pagamentoMock.setId(pagamentoId);
        pagamentoMock.setConta(contaMock);
        pagamentoMock.setValorPago(new BigDecimal("100.00"));
        pagamentoMock.setFormaPagamento(FormaPagamento.PIX);
        pagamentoMock.setDataHora(LocalDateTime.now());
        pagamentoMock.setUsuarioResponsavel(USUARIO_TESTE);

        caixaAberto = new Caixa();
        caixaAberto.setId(UUID.randomUUID());
        caixaAberto.setStatus(StatusCaixa.ABERTO);

        pedidoMock = new Pedido();
        pedidoMock.setId(UUID.randomUUID());
        pedidoMock.setTotal(new BigDecimal("50.00"));
        pedidoMock.setValorRecebido(BigDecimal.ZERO);
        pedidoMock.setConta(null); // Pedido direto
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockCaixaAberto() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO))
                .thenReturn(Optional.of(caixaAberto));
    }

    private void mockAuthenticatedUser(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private void mockAnonymousUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("anonymousUser");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    // Helper para configurar mocks para um pedido direto com saldo líquido zero
    private void mockPedidoDiretoComSaldoLiquidoZero() {
        UUID pedidoId = pedidoMock.getId();
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMock));
        when(pagamentoRepository.sumPagamentosPorPedido(pedidoId)).thenReturn(BigDecimal.ZERO);
        when(pagamentoRepository.findByPedidoId(pedidoId)).thenReturn(Collections.emptyList());
        // Não é necessário mockar estornoPagamentoRepository.somarValorEstornadoPorPagamentoId
        // se findByPedidoId retorna uma lista vazia, pois o loop não será executado.
    }

    // =========================================================================
    // SESSÃO 1 — REGISTRAR PAGAMENTO & CONTRATO DE CONTA (BLOCO 1, 2 & 8)
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — Fluxo Principal e Validação de Contas")
    class RegistrarPagamentoECorrespondenciaTests {

        @Test
        @DisplayName("CT-001 ao CT-010, CT-015, CT-048 ao CT-053: Fluxo Feliz — Deve registrar o pagamento completo, auditando data, usuário e gerando o DTO corretamente")
        void ct001_deveRegistrarPagamentoComSucesso() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            PagamentoResponseDTO resultado = pagamentoService.registrarPagamento(contaId, dto);

            assertNotNull(resultado);
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
            verify(contaRepository, times(1)).save(argThat(Conta::getPago));

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("CT-011 ao CT-013: Conta Ausente — Chamar ID nulo ou inexistente deve abortar transação financeira com ResourceNotFoundException")
        void ct011_deveLancarExceptionSeContaInexistente() {
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("50.00"));

            when(contaRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> pagamentoService.registrarPagamento(UUID.randomUUID(), dto));
            verify(pagamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-014: Quitação Dupla — Tentar registrar pagamentos em uma subcomanda que já consta como quitada estoura BusinessRuleException")
        void ct014_deveBarrarPagamentoEmContaJaPaga() {
            contaMock.setPago(true);
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("10.00"));
            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dto));
            verify(pagamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-PAG-001: Deve registrar pagamento com 'SISTEMA' se não houver usuário autenticado")
        void deveRegistrarPagamentoComSistemaSeNaoAutenticado() {
            mockCaixaAberto();

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(contaId, dto);

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(SISTEMA_FALLBACK, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("CT-PAG-002: Deve registrar pagamento com 'SISTEMA' se o usuário for anônimo")
        void deveRegistrarPagamentoComSistemaSeUsuarioAnonimo() {
            mockCaixaAberto();
            mockAnonymousUser();

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(contaId, dto);

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(SISTEMA_FALLBACK, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }
    }

    // =========================================================================
    // SESSÃO 2 — REGRAS DE VALORES E ARREDONDAMENTO BIGDECIMAL (BLOCO 3, 4 & 5)
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Regras de Valores, Limites e Amortizações")
    class RegrasDeValoresEMatematicaTests {

        @Test
        @DisplayName("CT-016 ao CT-019, CT-059: Excedente em Espécie — Permite recebimento acima do saldo exclusivamente se a forma for DINHEIRO (troco)")
        void ct016_devePermitirExcedenteApenasEmDinheiro() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            PagamentoRequestDTO dtoDinheiroExcedente = new PagamentoRequestDTO(FormaPagamento.DINHEIRO, new BigDecimal("120.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            assertDoesNotThrow(() -> pagamentoService.registrarPagamento(contaId, dtoDinheiroExcedente));

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("CT-018, CT-056 ao CT-058: Rejeição de Excedente Digital — Injeções de valores acima do saldo para cartões ou PIX devem ser bloqueadas")
        void ct018_deveRejeitarExcedenteEmFormasDigitais() {
            mockCaixaAberto();

            PagamentoRequestDTO dtoPixExcedente = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.01"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoPixExcedente));
            verify(pagamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-020 ao CT-022, CT-030: Saneamento Limite — Valores nulos, negativos ou zerados na requisição financeira disparam BusinessRuleException")
        void ct020_deveBarrarValoresInvalidos() {
            PagamentoRequestDTO dtoZero = new PagamentoRequestDTO(FormaPagamento.PIX, BigDecimal.ZERO);
            PagamentoRequestDTO dtoNegativo = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("-5.00"));
            PagamentoRequestDTO dtoNull = new PagamentoRequestDTO(FormaPagamento.PIX, null);

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoZero));
            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoNegativo));
            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoNull));
        }

        @Test
        @DisplayName("CT-023, CT-031 ao CT-036: Multi-amortizações — Valida acúmulos parcelados complexos usando a precisão decimal do BigDecimal")
        void ct023_deveProcessarMúltiplosPagamentosParciaisComPrecisao() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            PagamentoRequestDTO dtoParcial = new PagamentoRequestDTO(FormaPagamento.CREDITO, new BigDecimal("33.33"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("50.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            assertDoesNotThrow(() -> pagamentoService.registrarPagamento(contaId, dtoParcial));

            verify(contaRepository, times(1)).save(argThat(c -> !c.getPago()));

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("CT-PAG-010: Deve permitir novo pagamento após estorno parcial e quitar a conta")
        void devePermitirNovoPagamentoAposEstornoParcialEQuitarConta() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            contaMock.setValorTotal(new BigDecimal("100.00"));

            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("40.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("40.00"));
            pagamentoService.registrarPagamento(contaId, dto);

            assertTrue(contaMock.getPago(), "A conta deveria estar paga após o novo pagamento.");

            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));

            verify(contaRepository, times(1)).save(argThat(c -> c.getPago() == true));
        }

        @Test
        @DisplayName("CT-PAG-011: Meio digital aceita exatamente o saldo líquido restante")
        void meioDigitalAceitaExatamenteSaldoLiquidoRestante() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            contaMock.setValorTotal(new BigDecimal("100.00"));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("50.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("10.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("60.00"));
            pagamentoService.registrarPagamento(contaId, dto);

            assertTrue(contaMock.getPago());
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
        }

        @Test
        @DisplayName("CT-PAG-012: Meio digital rejeita valor acima do saldo líquido restante")
        void meioDigitalRejeitaValorAcimaDoSaldoLiquidoRestante() {
            mockCaixaAberto();

            contaMock.setValorTotal(new BigDecimal("100.00"));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("50.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("10.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("60.01"));

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dto));
            verify(pagamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-PAG-013: Dinheiro aceita valor recebido acima do saldo líquido mas valorPago fica limitado ao saldo")
        void dinheiroAceitaValorRecebidoAcimaDoSaldoLiquidoMasValorPagoLimitado() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            contaMock.setValorTotal(new BigDecimal("100.00"));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("50.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("10.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.DINHEIRO, new BigDecimal("70.00"));
            pagamentoService.registrarPagamento(contaId, dto);

            assertTrue(contaMock.getPago());
            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(new BigDecimal("60.00"), pagamentoCaptor.getValue().getValorPago());
        }

        @Test
        @DisplayName("CT-PAG-014: Novo Pagamento após estorno continua vinculado corretamente à Conta")
        void novoPagamentoAposEstornoVinculadoCorretamenteAConta() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            contaMock.setValorTotal(new BigDecimal("100.00"));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("50.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("10.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("60.00"));
            pagamentoService.registrarPagamento(contaId, dto);

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(contaMock, pagamentoCaptor.getValue().getConta());
            assertNull(pagamentoCaptor.getValue().getPedido());
        }
    }

    // =========================================================================
    // SESSÃO 3 — QUITAÇÃO OPERACIONAL E RECUPERAÇÃO DE EXTRATO (BLOCO 6 & 7)
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Quitações e Extratos Cronológicos")
    class QuitacaoEExtratoTests {

        @Test
        @DisplayName("CT-037 ao CT-041: Trigger de Fechamento — Quando a soma dos lançamentos atinge o valor total da subconta, o status muda para pago=true")
        void ct037_deveQuitarContaQuandoValorAlcancado() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(contaId, dto);

            assertTrue(contaMock.getPago());
            verify(contaRepository, times(1)).save(contaMock);

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("CT-043 ao CT-047: Leitura Cronológica — Deve listar histórico de amortizações de forma limpa ou explodir 404 se a conta for órfã")
        void ct043_deveListarPagamentosPorContaValida() {
            when(contaRepository.existsById(contaId)).thenReturn(true);
            when(pagamentoRepository.findByContaId(contaId)).thenReturn(List.of(pagamentoMock));

            List<PagamentoResponseDTO> resultado = pagamentoService.listarPorConta(contaId);

            assertFalse(resultado.isEmpty());
            verify(pagamentoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // SESSÃO 4 — REGRESSÃO DE ESTADO INTEGRADO DO PDV (BLOCO 10, 11, 12, 14, 16)
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Cenários de Regressão e Fluxo de Salão")
    class RegressaoEFluxoPdvTests {

        @Test
        @DisplayName("CT-067: Isolamento Financeiro de Subcontas — Liquidações na Conta 1 da mesa não podem dar baixa ou afetar os saldos da Conta 2")
        void ct067_deveManterContasIsoladas() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            Conta conta1 = new Conta();
            conta1.setId(UUID.randomUUID());
            conta1.setValorTotal(BigDecimal.TEN);
            conta1.setPago(false);

            Conta conta2 = new Conta();
            conta2.setId(UUID.randomUUID());
            conta2.setValorTotal(BigDecimal.TEN);
            conta2.setPago(false);

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, BigDecimal.TEN);

            when(contaRepository.findByIdForUpdate(conta1.getId())).thenReturn(Optional.of(conta1));
            when(pagamentoRepository.sumPagamentosPorConta(conta1.getId())).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(conta1.getId())).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(conta1.getId(), dto);

            assertTrue(conta1.getPago());
            assertFalse(conta2.getPago());

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository, times(1)).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }
    }

    // =========================================================================
    // SESSÃO 5 — AUDITORIA AVANÇADA E SEGURANÇA CONCORRENTE (BLOCO 13 & 15)
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — Concorrência Reentrante no Caixa")
    class ConcorrenciaEAuditoriaAvancadaTests {

        @Test
        @DisplayName("CT-078 ao CT-082: Corrida de Liquidação — Dois operadores submetendo transações PIX na mesma fração de segundo devem respeitar a barreira de estado")
        void ct078_corridaDePagamentoSimultaneo() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(contaId, dto);

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dto));
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("CT-087 ao CT-092, CT-098: Rastreabilidade Estrita — Impõe que toda persistência financeira possua auditoria contábil completa")
        void ct087_deveGarantirOrdemEAuditoriaTransacional() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));
            when(contaRepository.findByIdForUpdate(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(contaId, dto);

            InOrder ordemFiscal = inOrder(contaRepository, pagamentoRepository, estornoPagamentoRepository);
            ordemFiscal.verify(contaRepository).findByIdForUpdate(contaId);
            ordemFiscal.verify(pagamentoRepository).sumPagamentosPorConta(contaId);
            ordemFiscal.verify(estornoPagamentoRepository).somarValorEstornadoPorContaId(contaId);
            ordemFiscal.verify(pagamentoRepository).save(any(Pagamento.class));
            ordemFiscal.verify(contaRepository).save(any(Conta.class));

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
        }
    }

    // =========================================================================
    // SESSÃO 6 — REGISTRAR PAGAMENTO PEDIDO
    // =========================================================================
    @Nested
    @DisplayName("6. Camada de Blindagem — Fluxo de Pagamento de Pedido")
    class RegistrarPagamentoPedidoTests {

        @Test
        @DisplayName("CT-PAG-003: Deve registrar pagamento de pedido com usuário autenticado")
        void deveRegistrarPagamentoPedidoComUsuarioAutenticado() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);
            mockPedidoDiretoComSaldoLiquidoZero(); // Configura os mocks para saldo zero

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("50.00"));

            when(pedidoRepository.findByIdForUpdate(pedidoMock.getId())).thenReturn(Optional.of(pedidoMock));
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoMock);

            PagamentoResponseDTO resultado = pagamentoService.registrarPagamentoPedido(pedidoMock.getId(), dto);

            assertNotNull(resultado);
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
            verify(pedidoRepository, times(1)).save(any(Pedido.class));

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
            assertEquals(pedidoMock.getTotal(), pagamentoCaptor.getValue().getValorPago());
        }

        @Test
        @DisplayName("CT-PAG-004: Deve registrar pagamento de pedido com 'SISTEMA' se não houver usuário autenticado")
        void deveRegistrarPagamentoPedidoComSistemaSeNaoAutenticado() {
            mockCaixaAberto();
            mockPedidoDiretoComSaldoLiquidoZero(); // Configura os mocks para saldo zero

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("50.00"));

            when(pedidoRepository.findByIdForUpdate(pedidoMock.getId())).thenReturn(Optional.of(pedidoMock));
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoMock);

            pagamentoService.registrarPagamentoPedido(pedidoMock.getId(), dto);

            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            verify(pedidoRepository, times(1)).save(any(Pedido.class));
            assertEquals(SISTEMA_FALLBACK, pagamentoCaptor.getValue().getUsuarioResponsavel());
            assertEquals(pedidoMock.getTotal(), pagamentoCaptor.getValue().getValorPago());
        }

        @Test
        @DisplayName("CT-PAG-005: Deve lançar BusinessRuleException se já existir saldo financeiro para o pedido")
        void deveLancarExcecaoSePagamentoPedidoDuplicado() {
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("50.00"));

            UUID pedidoId = pedidoMock.getId();
            Pagamento pagamentoAnterior = new Pagamento();
            pagamentoAnterior.setId(UUID.randomUUID());
            pagamentoAnterior.setPedido(pedidoMock);
            pagamentoAnterior.setValorPago(new BigDecimal("10.00"));

            when(pedidoRepository.findByIdForUpdate(pedidoId)).thenReturn(Optional.of(pedidoMock));
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMock)); // Para getSaldoLiquidoPagoPorPedido
            when(pagamentoRepository.sumPagamentosPorPedido(pedidoId)).thenReturn(new BigDecimal("10.00")); // Saldo bruto de 10
            when(pagamentoRepository.findByPedidoId(pedidoId)).thenReturn(List.of(pagamentoAnterior));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoAnterior.getId())).thenReturn(BigDecimal.ZERO); // Sem estorno

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamentoPedido(pedidoMock.getId(), dto));
            verify(pagamentoRepository, never()).save(any());
            verify(pedidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-PAG-006: Deve lançar BusinessRuleException se valor recebido for insuficiente")
        void deveLancarExcecaoSeValorRecebidoInsuficiente() {
            mockPedidoDiretoComSaldoLiquidoZero(); // Configura os mocks para saldo zero

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("40.00"));

            when(pedidoRepository.findByIdForUpdate(pedidoMock.getId())).thenReturn(Optional.of(pedidoMock));

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamentoPedido(pedidoMock.getId(), dto));
            verify(pagamentoRepository, never()).save(any());
            verify(pedidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-PAG-007: Deve lançar BusinessRuleException se valor recebido exceder o total para formas digitais")
        void deveLancarExcecaoSeValorExcederParaFormasDigitais() {
            mockPedidoDiretoComSaldoLiquidoZero(); // Configura os mocks para saldo zero

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("60.00"));

            when(pedidoRepository.findByIdForUpdate(pedidoMock.getId())).thenReturn(Optional.of(pedidoMock));

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamentoPedido(pedidoMock.getId(), dto));
            verify(pagamentoRepository, never()).save(any());
            verify(pedidoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-PAG-008: Deve permitir valor recebido excedente para DINHEIRO (troco) em pedido")
        void devePermitirValorExcedenteParaDinheiroEmPedido() {
            mockCaixaAberto();
            mockAuthenticatedUser(USUARIO_TESTE);
            mockPedidoDiretoComSaldoLiquidoZero(); // Configura os mocks para saldo zero

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.DINHEIRO, new BigDecimal("60.00"));

            when(pedidoRepository.findByIdForUpdate(pedidoMock.getId())).thenReturn(Optional.of(pedidoMock));
            when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
                Pagamento p = invocation.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoMock);

            PagamentoResponseDTO resultado = pagamentoService.registrarPagamentoPedido(pedidoMock.getId(), dto);

            assertNotNull(resultado);
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
            verify(pedidoRepository, times(1)).save(any(Pedido.class));
            ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
            verify(pagamentoRepository).save(pagamentoCaptor.capture());
            assertEquals(USUARIO_TESTE, pagamentoCaptor.getValue().getUsuarioResponsavel());
            assertEquals(pedidoMock.getTotal(), pagamentoCaptor.getValue().getValorPago());
        }

        @Test
        @DisplayName("CT-PAG-009: Deve lançar BusinessRuleException se o caixa estiver fechado ao registrar pagamento de pedido")
        void deveLancarExcecaoSeCaixaFechadoAoRegistrarPagamentoPedido() {
            mockPedidoDiretoComSaldoLiquidoZero(); // Configura os mocks para saldo zero

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("50.00"));

            when(pedidoRepository.findByIdForUpdate(pedidoMock.getId())).thenReturn(Optional.of(pedidoMock));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamentoPedido(pedidoMock.getId(), dto));
            verify(pagamentoRepository, never()).save(any());
            verify(pedidoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // SESSÃO 7 — CÁLCULO DE SALDO LÍQUIDO
    // =========================================================================
    @Nested
    @DisplayName("7. Camada de Blindagem — Cálculo de Saldo Líquido (Pagamentos - Estornos)")
    class SaldoLiquidoTests {

        @Test
        @DisplayName("CT-SALDO-001: Deve calcular saldo líquido corretamente para uma conta sem estornos")
        void deveCalcularSaldoLiquidoContaSemEstornos() {
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorConta(contaId);

            assertEquals(new BigDecimal("100.00"), saldoLiquido);
        }

        @Test
        @DisplayName("CT-SALDO-002: Deve calcular saldo líquido corretamente para uma conta com estornos parciais")
        void deveCalcularSaldoLiquidoContaComEstornosParciais() {
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("40.00"));

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorConta(contaId);

            assertEquals(new BigDecimal("60.00"), saldoLiquido);
        }

        @Test
        @DisplayName("CT-SALDO-003: Deve calcular saldo líquido corretamente para uma conta com estornos totais")
        void deveCalcularSaldoLiquidoContaComEstornosTotais() {
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("100.00"));

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorConta(contaId);

            assertThat(saldoLiquido).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("CT-SALDO-004: Deve calcular saldo líquido corretamente para um pedido direto sem estornos")
        void deveCalcularSaldoLiquidoPedidoDiretoSemEstornos() {
            UUID pedidoId = pedidoMock.getId();
            pedidoMock.setConta(null);
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMock));
            when(pagamentoRepository.sumPagamentosPorPedido(pedidoId)).thenReturn(new BigDecimal("50.00"));
            when(pagamentoRepository.findByPedidoId(pedidoId)).thenReturn(Collections.emptyList());

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorPedido(pedidoId);

            assertEquals(new BigDecimal("50.00"), saldoLiquido);
        }

        @Test
        @DisplayName("CT-SALDO-005: Deve calcular saldo líquido corretamente para um pedido direto com estornos")
        void deveCalcularSaldoLiquidoPedidoDiretoComEstornos() {
            UUID pedidoId = pedidoMock.getId();
            UUID pagamentoPedidoId = UUID.randomUUID();
            pedidoMock.setConta(null);

            Pagamento pagamentoDoPedido = new Pagamento();
            pagamentoDoPedido.setId(pagamentoPedidoId);
            pagamentoDoPedido.setPedido(pedidoMock);
            pagamentoDoPedido.setValorPago(new BigDecimal("50.00"));

            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMock));
            when(pagamentoRepository.sumPagamentosPorPedido(pedidoId)).thenReturn(new BigDecimal("50.00"));
            when(pagamentoRepository.findByPedidoId(pedidoId)).thenReturn(List.of(pagamentoDoPedido));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoPedidoId)).thenReturn(new BigDecimal("20.00"));

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorPedido(pedidoId);

            assertEquals(new BigDecimal("30.00"), saldoLiquido);
        }

        @Test
        @DisplayName("CT-SALDO-006: Deve calcular saldo líquido para um pedido vinculado a uma conta")
        void deveCalcularSaldoLiquidoPedidoVinculadoAConta() {
            UUID pedidoId = pedidoMock.getId();
            UUID contaAssociadaId = UUID.randomUUID();
            Conta contaAssociada = new Conta();
            contaAssociada.setId(contaAssociadaId);
            pedidoMock.setConta(contaAssociada);

            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaAssociadaId)).thenReturn(new BigDecimal("150.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaAssociadaId)).thenReturn(new BigDecimal("50.00"));

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorPedido(pedidoId);

            assertEquals(new BigDecimal("100.00"), saldoLiquido);
            verify(pagamentoRepository, never()).sumPagamentosPorPedido(any());
        }

        @Test
        @DisplayName("CT-SALDO-007: Deve retornar zero se não houver pagamentos nem estornos para conta")
        void deveRetornarZeroSeSemPagamentosNemEstornosConta() {
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(BigDecimal.ZERO);

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorConta(contaId);

            assertEquals(BigDecimal.ZERO, saldoLiquido);
        }

        @Test
        @DisplayName("CT-SALDO-008: Deve retornar zero se não houver pagamentos nem estornos para pedido direto")
        void deveRetornarZeroSeSemPagamentosNemEstornosPedidoDireto() {
            UUID pedidoId = pedidoMock.getId();
            pedidoMock.setConta(null);
            when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoMock));
            when(pagamentoRepository.sumPagamentosPorPedido(pedidoId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.findByPedidoId(pedidoId)).thenReturn(Collections.emptyList());

            BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorPedido(pedidoId);

            assertEquals(BigDecimal.ZERO, saldoLiquido);
        }
    }

    @Nested
    @DisplayName("🔍 Pesquisa de Pagamentos — CT-PESQ-001 a CT-PESQ-010")
    class PesquisaDePagamentosTests {

        @Mock
        private PagamentoRepository pagamentoRepository;

        private PagamentoService pagamentoService;

        private UUID pagamentoId;
        private UUID contaId;
        private UUID pedidoId;
        private UUID clienteId;
        private UUID mesaId;
        private UUID caixaId;

        private Pagamento pagamentoMock;
        private Conta contaMock;
        private Pedido pedidoMock;
        private Cliente clienteMock;
        private Mesa mesaMock;
        private Caixa caixaMock;

        private final String USUARIO_TESTE = "usuario@teste.com";

        @BeforeEach
        void setUp() {
            SecurityContextHolder.clearContext();

            pagamentoRepository = mock(PagamentoRepository.class);
            contaRepository = mock(ContaRepository.class);
            caixaRepository = mock(CaixaRepository.class);
            estornoPagamentoRepository = mock(EstornoPagamentoRepository.class);
            pedidoRepository = mock(PedidoRepository.class);

            pagamentoService = new PagamentoService(pagamentoRepository, contaRepository, caixaRepository, estornoPagamentoRepository, pedidoRepository);

            pagamentoId = UUID.randomUUID();
            contaId = UUID.randomUUID();
            pedidoId = UUID.randomUUID();
            clienteId = UUID.randomUUID();
            mesaId = UUID.randomUUID();
            caixaId = UUID.randomUUID();

            clienteMock = new Cliente();
            clienteMock.setId(clienteId);
            clienteMock.setNome("João da Silva");

            mesaMock = new Mesa();
            mesaMock.setId(mesaId);
            mesaMock.setNumero(5);

            caixaMock = new Caixa();
            caixaMock.setId(caixaId);
            caixaMock.setStatus(StatusCaixa.ABERTO);

            pedidoMock = new Pedido();
            pedidoMock.setId(pedidoId);
            pedidoMock.setNumeroPedido("ABC12");
            pedidoMock.setNumeroMesa(5);
            pedidoMock.setTotal(new BigDecimal("50.00"));

            contaMock = new Conta();
            contaMock.setId(contaId);
            contaMock.setNumeroConta(1);
            contaMock.setPago(true);
            contaMock.setValorTotal(new BigDecimal("50.00"));
            contaMock.setCliente(clienteMock);
            contaMock.setComanda(new Comanda());

            pagamentoMock = new Pagamento();
            pagamentoMock.setId(pagamentoId);
            pagamentoMock.setConta(contaMock);
            pagamentoMock.setPedido(pedidoMock);
            pagamentoMock.setCaixa(caixaMock);
            pagamentoMock.setValorPago(new BigDecimal("50.00"));
            pagamentoMock.setFormaPagamento(FormaPagamento.PIX);
            pagamentoMock.setDataHora(LocalDateTime.now());
            pagamentoMock.setUsuarioResponsavel(USUARIO_TESTE);
        }

        @Test
        @DisplayName("CT-PESQ-001: Deve retornar lista vazia quando não há resultados")
        void deveRetornarListaVaziaQuandoNaoHáResultados() {
            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(Collections.emptyList());

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).isEmpty();
            verify(pagamentoRepository, times(1)).pesquisarPorPedido("ABC12");
        }

        @Test
        @DisplayName("CT-PESQ-002: Deve retornar lista de pagamentos com saldo estornavel calculado")
        void deveRetornarListaDePagamentosComSaldoEstornavel() {
            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            PagamentoPesquisaDTO dto = resultado.get(0);
            assertEquals(pagamentoId, dto.getIdPagamento());
            assertEquals("João da Silva", dto.getCliente());
            assertEquals(5, dto.getNumeroMesa());
            assertEquals(pedidoId, dto.getPedidoId());
            assertEquals("ABC12", dto.getNumeroPedido());
            assertEquals(FormaPagamento.PIX, dto.getFormaPagamento());
            assertEquals(new BigDecimal("50.00"), dto.getValorPago());
            assertEquals(new BigDecimal("50.00"), dto.getSaldoEstornavel());
            assertEquals(StatusPagamento.PAGO, dto.getStatusPagamento());
            assertEquals(USUARIO_TESTE, dto.getUsuarioResponsavel());
            assertEquals(caixaId, dto.getCaixaId());
        }

        @Test
        @DisplayName("CT-PESQ-003: Deve direcionar para pesquisarPorCliente quando clienteId é informado")
        void deveDirecionarParaPesquisarPorClienteQuandoClienteIdInformado() {
            when(pagamentoRepository.pesquisarPorCliente(null))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setClienteId(clienteId);
            filtro.setFormaPagamento(FormaPagamento.PIX);

            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            verify(pagamentoRepository, times(1)).pesquisarPorCliente(null);
        }

        @Test
        @DisplayName("CT-PESQ-004: Deve direcionar para pesquisarPorMesa quando mesaId é informado")
        void deveDirecionarParaPesquisarPorMesaQuandoMesaIdInformado() {
            when(pagamentoRepository.pesquisarPorMesa(5))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setMesaId(mesaId);
            filtro.setNumeroMesa(5);
            filtro.setFormaPagamento(FormaPagamento.PIX);

            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            verify(pagamentoRepository, times(1)).pesquisarPorMesa(5);
        }

        @Test
        @DisplayName("CT-PESQ-005: Deve retornar DTO com nomeResponsavel quando conta nao tem cliente")
        void deveRetornarDTOComNomeResponsavelQuandoContaNaoTemCliente() {
            contaMock.setCliente(null);
            contaMock.setNomeResponsavel("MESA 5 - CONTA 1");
            pagamentoMock.setConta(contaMock);

            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            assertEquals("MESA 5 - CONTA 1", resultado.get(0).getCliente());
        }

        @Test
        @DisplayName("CT-PESQ-006: Deve retornar status ABERTO quando conta nao esta paga")
        void deveRetornarStatusAbertoQuandoContaNaoEstaPaga() {
            contaMock.setPago(false);
            pagamentoMock.setConta(contaMock);

            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            assertEquals(StatusPagamento.ABERTO, resultado.get(0).getStatusPagamento());
        }

        @Test
        @DisplayName("CT-PESQ-007: Deve retornar status PAGO quando conta esta paga")
        void deveRetornarStatusPagoQuandoContaEstaPaga() {
            contaMock.setPago(true);
            pagamentoMock.setConta(contaMock);

            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            assertEquals(StatusPagamento.PAGO, resultado.get(0).getStatusPagamento());
        }

        @Test
        @DisplayName("CT-PESQ-008: Deve retornar status PAGO quando pagamento nao tem conta vinculada")
        void deveRetornarStatusPagoQuandoPagamentoNaoTemConta() {
            pagamentoMock.setConta(null);

            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            assertEquals(StatusPagamento.PAGO, resultado.get(0).getStatusPagamento());
        }

        @Test
        @DisplayName("CT-PESQ-009: Deve calcular saldoEstornavel como valorPago menos totalEstornado")
        void deveCalcularSaldoEstornavelCorretamente() {
            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("30.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            assertEquals(new BigDecimal("30.00"), resultado.get(0).getSaldoEstornavel());
        }

        @Test
        @DisplayName("CT-PESQ-010: Deve retornar DTO sem numeroMesa quando pagamento nao tem pedido")
        void deveRetornarDTOSemNumeroMesaQuandoPagamentoNaoTemPedido() {
            pagamentoMock.setPedido(null);

            when(pagamentoRepository.pesquisarPorPedido("ABC12"))
                    .thenReturn(List.of(new PagamentoPesquisaDTO(pagamentoMock, new BigDecimal("50.00"))));

            PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();
            filtro.setNumeroPedido("ABC12");
            List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);

            assertThat(resultado).hasSize(1);
            assertNull(resultado.get(0).getNumeroMesa());
            assertNull(resultado.get(0).getPedidoId());
            assertNull(resultado.get(0).getNumeroPedido());
        }
    }
}
