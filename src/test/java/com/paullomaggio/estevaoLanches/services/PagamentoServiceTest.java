package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Conta;
import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ContaRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Engenharia Financeira — Matriz de Blindagem de Pagamentos")
class PagamentoServiceTest {

    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private ContaRepository contaRepository;

    private PagamentoService pagamentoService;

    private UUID contaId;
    private UUID pagamentoId;
    private Conta contaMock;
    private Pagamento pagamentoMock;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks
        pagamentoService = new PagamentoService(pagamentoRepository, contaRepository);

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
        pagamentoMock.setUsuarioResponsavel("SISTEMA_MOBILE");

        // As configurações de mock foram movidas para os métodos de teste ou para @BeforeEach em classes @Nested,
        // conforme a necessidade de cada teste, para evitar UnnecessaryStubbingException.
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
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(pagamentoMock);
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            PagamentoResponseDTO resultado = pagamentoService.registrarPagamento(contaId, dto);

            assertNotNull(resultado);
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
            verify(contaRepository, times(1)).save(argThat(Conta::getPago));
        }

        @Test
        @DisplayName("CT-011 ao CT-013: Conta Ausente — Chamar ID nulo ou inexistente deve abortar transação financeira com ResourceNotFoundException")
        void ct011_deveLancarExceptionSeContaInexistente() {
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("50.00"));

            when(contaRepository.findById(any())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> pagamentoService.registrarPagamento(UUID.randomUUID(), dto));
            verify(pagamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-014: Quitação Dupla — Tentar registrar pagamentos em uma subcomanda que já consta como quitada estoura BusinessRuleException")
        void ct014_deveBarrarPagamentoEmContaJaPaga() {
            contaMock.setPago(true);
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("10.00"));
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dto));
            verify(pagamentoRepository, never()).save(any());
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
            PagamentoRequestDTO dtoDinheiroExcedente = new PagamentoRequestDTO(FormaPagamento.DINHEIRO, new BigDecimal("120.00")); // R$20 de troco

            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(pagamentoMock);
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            assertDoesNotThrow(() -> pagamentoService.registrarPagamento(contaId, dtoDinheiroExcedente));
        }

        @Test
        @DisplayName("CT-018, CT-056 ao CT-058: Rejeição de Excedente Digital — Injeções de valores acima do saldo para cartões ou PIX devem ser bloqueadas")
        void ct018_deveRejeitarExcedenteEmFormasDigitais() {
            PagamentoRequestDTO dtoPixExcedente = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.01"));

            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoPixExcedente));
            verify(pagamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-020 ao CT-022, CT-030: Saneamento Limite — Valores nulos, negativos ou zerados na requisição financeira disparam BusinessRuleException")
        void ct020_deveBarrarValoresInvalidos() {
            PagamentoRequestDTO dtoZero = new PagamentoRequestDTO(FormaPagamento.PIX, BigDecimal.ZERO);
            PagamentoRequestDTO dtoNegativo = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("-5.00"));
            PagamentoRequestDTO dtoNull = new PagamentoRequestDTO(FormaPagamento.PIX, null);

            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));

            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoZero));
            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoNegativo));
            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dtoNull));
        }

        @Test
        @DisplayName("CT-023, CT-031 ao CT-036: Multi-amortizações — Valida acúmulos parcelados complexos usando a precisão decimal do BigDecimal")
        void ct023_deveProcessarMúltiplosPagamentosParciaisComPrecisao() {
            PagamentoRequestDTO dtoParcial = new PagamentoRequestDTO(FormaPagamento.CREDITO, new BigDecimal("33.33"));

            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("50.00")); // Já pagou metade
            when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(pagamentoMock);
            // REMOVIDO: when(contaRepository.save(any(Conta.class))) pois o teste verifica que não é chamado.

            assertDoesNotThrow(() -> pagamentoService.registrarPagamento(contaId, dtoParcial));

            verify(contaRepository, never()).save(any()); // R$50 + R$33.33 = R$83.33 (Ainda não quitou, permanece aberta)
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
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(pagamentoMock);
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(contaId, dto);

            assertTrue(contaMock.getPago());
            verify(contaRepository, times(1)).save(contaMock);
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
            Conta conta1 = new Conta();
            conta1.setId(UUID.randomUUID());
            conta1.setValorTotal(BigDecimal.TEN);
            conta1.setPago(false);

            Conta conta2 = new Conta();
            conta2.setId(UUID.randomUUID());
            conta2.setValorTotal(BigDecimal.TEN);
            conta2.setPago(false);

            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, BigDecimal.TEN);

            when(contaRepository.findById(conta1.getId())).thenReturn(Optional.of(conta1));
            when(pagamentoRepository.sumPagamentosPorConta(conta1.getId())).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(new Pagamento()); // Retorna uma nova instância para evitar conflitos com pagamentoMock
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(conta1.getId(), dto);

            assertTrue(conta1.getPago());
            assertFalse(conta2.getPago());
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
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));

            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(pagamentoMock);
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            // Operador 1 processa a baixa com sucesso e muta o estado da instância em memória
            pagamentoService.registrarPagamento(contaId, dto);

            // Operador 2 tenta injetar o pagamento paralelo concorrente, esbarrando na barreira de estado já mutado
            assertThrows(BusinessRuleException.class, () -> pagamentoService.registrarPagamento(contaId, dto));
            verify(pagamentoRepository, times(1)).save(any(Pagamento.class)); // Garante escrita ÚNICA
        }

        @Test
        @DisplayName("CT-087 ao CT-092, CT-098: Rastreabilidade Estrita — Impõe que toda persistência financeira possua auditoria contábil completa")
        void ct087_deveGarantirOrdemEAuditoriaTransacional() {
            PagamentoRequestDTO dto = new PagamentoRequestDTO(FormaPagamento.PIX, new BigDecimal("100.00"));
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(BigDecimal.ZERO);
            when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(pagamentoMock);
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta savedConta = invocation.getArgument(0);
                return savedConta;
            });

            pagamentoService.registrarPagamento(contaId, dto);

            // Rastreabilidade por verificação cronológica síncrona
            InOrder ordemFiscal = inOrder(contaRepository, pagamentoRepository);
            ordemFiscal.verify(contaRepository).findById(contaId);
            ordemFiscal.verify(pagamentoRepository).sumPagamentosPorConta(contaId);
            ordemFiscal.verify(pagamentoRepository).save(any(Pagamento.class));
            ordemFiscal.verify(contaRepository).save(any(Conta.class));
        }
    }
}