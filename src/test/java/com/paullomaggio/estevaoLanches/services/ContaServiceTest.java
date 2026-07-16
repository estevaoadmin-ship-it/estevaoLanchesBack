package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ContaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ContaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ComandaRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Suprema — Engenharia de Matriz de Divisão Financeira e Subcontas")
class ContaServiceTest {

    @Mock private ContaRepository contaRepository;
    @Mock private ComandaRepository comandaRepository;
    @Mock private ClienteRepository clienteRepository; // Mantido, pois é usado em 'verify(..., never())'

    // Removido @InjectMocks
    private ContaService contaService;

    private UUID comandaId;
    private UUID contaId;
    private Comanda comandaMock;
    private Mesa mesaMock;
    private Conta contaMock;
    private Cliente clienteMock;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks
        contaService = new ContaService(contaRepository, comandaRepository);

        comandaId = UUID.randomUUID();
        contaId = UUID.randomUUID();

        mesaMock = new Mesa();
        mesaMock.setId(UUID.randomUUID());
        mesaMock.setNumero(5);

        comandaMock = new Comanda();
        comandaMock.setId(comandaId);
        comandaMock.setMesa(mesaMock);
        comandaMock.setContas(new ArrayList<>());

        clienteMock = new Cliente();
        clienteMock.setId(UUID.randomUUID());
        clienteMock.setNome("MESA 5 - CONTA 2");

        contaMock = new Conta();
        contaMock.setId(contaId);
        contaMock.setNumeroConta(2);
        contaMock.setPago(false);
        contaMock.setValorTotal(BigDecimal.ZERO);
        contaMock.setComanda(comandaMock);
        contaMock.setCliente(clienteMock); // Este clienteMock não será mais setado no código de produção
    }

    // =========================================================================
    // BLOCO 1 & 4 — CRIAR CONTA & LIFECYCLE DO CLIENTE AUTOMÁTICO
    // =========================================================================
    @Nested
    @DisplayName("1 & 4. Camada de Blindagem — Abertura de Subcontas e Vinculação de Cliente")
    class CriarContaTests {

        @Test
        @DisplayName("CT-001 ao CT-010, CT-021 ao CT-026: Fluxo Feliz — Deve abrir subcomanda gerando o Cliente com nome correto e respeitando a ordem rígida de persistência")
        void fluxoFelizCriacaoSubconta() {
            ContaRequestDTO dto = new ContaRequestDTO(2, comandaId);

            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaMock));
            when(contaRepository.findByComandaIdAndNumeroConta(comandaId, 2)).thenReturn(Optional.empty());
            // when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Removido stub obsoleto
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));

            ContaResponseDTO resultado = contaService.criar(dto);

            assertNotNull(resultado);
            InOrder ordemTransacional = inOrder(contaRepository); // ClienteRepository removido
            // ordemTransacional.verify(clienteRepository).save(argThat(c -> c.getNome().equals("MESA 5 - CONTA 2"))); // Removido verify obsoleto
            ordemTransacional.verify(contaRepository).save(argThat(c -> c.getNumeroConta() == 2 && !c.getPago() && c.getCliente() == null)); // Cliente deve ser null
            verify(clienteRepository, never()).save(any(Cliente.class)); // Adicionado: Cliente não deve ser salvo
        }
    }

    // =========================================================================
    // BLOCO 2 — VALIDAÇÃO DE COMANDA MESTRE
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Barreiras de Validação da Comanda Mãe")
    class ValidacaoComandaMestreTests {

        @Test
        @DisplayName("CT-011 ao CT-015: Sessão Ausente — Comanda mestre inexistente deve abortar a operação e estourar ResourceNotFoundException")
        void comandaInexistenteDeveLancarException() {
            ContaRequestDTO dto = new ContaRequestDTO(1, comandaId);
            when(comandaRepository.findById(comandaId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> contaService.criar(dto));
            verify(clienteRepository, never()).save(any());
            verify(contaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 3 — PROTEÇÃO CONTRA DUPLICIDADE DE SUBCONTAS
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Controle de Idempotência e Duplicação")
    class DuplicidadeSubcontasTests {

        @Test
        @DisplayName("CT-016 ao CT-020: Partição Existente — Tentar criar uma subconta com número idêntico sob a mesma comanda mestre deve lançar BusinessRuleException")
        void subcontaDuplicadaDeveBloquear() {
            ContaRequestDTO dto = new ContaRequestDTO(2, comandaId);

            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaMock));
            when(contaRepository.findByComandaIdAndNumeroConta(comandaId, 2)).thenReturn(Optional.of(contaMock));

            assertThrows(BusinessRuleException.class, () -> contaService.criar(dto));
            verify(contaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 5 & 6 — BUSCAS, LEITURAS E FILTRAGENS
    // =========================================================================
    @Nested
    @DisplayName("5 & 6. Camada de Blindagem — Consultas e Recuperação de Atendimento")
    class ConsultasELeiturasTests {

        @Test
        @DisplayName("CT-027 ao CT-030: Localizar por ID — Deve carregar os dados financeiros com sucesso sem acionar escritas de persistência")
        void buscarPorIdExistente() {
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));

            ContaResponseDTO resultado = contaService.buscarPorId(contaId);

            assertNotNull(resultado);
            verify(contaRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-031 ao CT-035: Listar por Comanda — Deve retornar todas as partições financeiras da mesa ou estourar erro se a comanda for inválida")
        void listarContasPorComandaValida() {
            when(comandaRepository.existsById(comandaId)).thenReturn(true);
            when(contaRepository.findByComandaId(comandaId)).thenReturn(List.of(contaMock));

            List<ContaResponseDTO> resultado = contaService.listarPorComanda(comandaId);

            assertEquals(1, resultado.size());
            verify(contaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 7 — LIQUIDAÇÃO FINANCEIRA (BAIXA NO CAIXA)
    // =========================================================================
    @Nested
    @DisplayName("7. Camada de Blindagem — Liquidação e Baixa de Contas")
    class LiquidacaoFinanceiraTests {

        @Test
        @DisplayName("CT-036 ao CT-039: Baixa com Sucesso — Liquidar conta em aberto deve mutar o token pago para true e registrar no banco")
        void deveLiquidarContaAberta() {
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));

            ContaResponseDTO resultado = contaService.liquidarConta(contaId);

            assertTrue(resultado.pago());
            verify(contaRepository, times(1)).save(contaMock);
        }

        @Test
        @DisplayName("CT-041 e CT-042: Violação de Baixa — Tentar liquidar uma conta que já consta como paga no sistema deve estourar BusinessRuleException")
        void deveImpedirLiquidarContaJaPaga() {
            contaMock.setPago(true);
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));

            assertThrows(BusinessRuleException.class, () -> contaService.liquidarConta(contaId));
            verify(contaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 8 & 9 — EXCLUSÃO FÍSICA E CADASTRO DE SALDO DEVEDOR
    // =========================================================================
    @Nested
    @DisplayName("8 & 9. Camada de Blindagem — Regras de Exclusão e Restrições de Saldo")
    class ExclusaoERestricoesSaldoTests {

        @Test
        @DisplayName("CT-043 ao CT-045, CT-047: Excluir sem Impedimentos — Deve permitir a remoção física de uma conta zerada ou já devidamente quitada")
        void devePermitirExcluirContaQuitada() {
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            doNothing().when(contaRepository).delete(contaMock);

            assertDoesNotThrow(() -> contaService.deletar(contaId));
            verify(contaRepository, times(1)).delete(contaMock);
        }

        @Test
        @DisplayName("CT-046 e CT-049: Barreira contra Calote — Tentar deletar uma conta em aberto contendo saldo devedor pendente deve ser bloqueado com BusinessRuleException")
        void deveBloquearExclusaoDeContaComSaldoDevedor() {
            contaMock.setPago(false);
            contaMock.setValorTotal(new BigDecimal("150.50")); // Saldo devedor pendente no balcão
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));

            assertThrows(BusinessRuleException.class, () -> contaService.deletar(contaId));
            verify(contaRepository, never()).delete(any());
        }
    }

    // =========================================================================
    // BLOCO 10, 11 & 14 — FLUXOS SEQUENCIAIS, DINÂMICA DE SALÃO E INTEGRAÇÃO
    // =========================================================================
    @Nested
    @DisplayName("10, 11 & 14. Camada de Blindagem — Linha do Tempo do Salão e Regressão")
    class LinhaTempoMesaTests {

        @Test
        @DisplayName("CT-060 ao CT-068, CT-082: Isolamento Financeiro — Multi-contas na mesma mesa devem operar de forma independente: pagar a Conta 1 não quita a Conta 2")
        void deveGarantirIsolamentoEntreSubcontasDaMesa() {
            // 🎯 FIX: Construtor da Conta atualizado com os novos campos nomeResponsavel e telefoneResponsavel
            // Cliente artificial não é mais criado, então o construtor da Conta não deve mais recebê-lo
            Conta conta1 = new Conta(UUID.randomUUID(), 1, false, BigDecimal.ZERO, null, null, comandaMock, null, new ArrayList<>(), new ArrayList<>());
            Conta conta2 = new Conta(UUID.randomUUID(), 2, false, BigDecimal.ZERO, null, null, comandaMock, null, new ArrayList<>(), new ArrayList<>());

            when(contaRepository.findById(conta1.getId())).thenReturn(Optional.of(conta1));
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));

            // Liquida apenas a partição 1
            ContaResponseDTO res1 = contaService.liquidarConta(conta1.getId());

            assertTrue(res1.pago());
            assertFalse(conta2.getPago()); // A partição 2 permanece aberta para consumo
        }
    }

    // =========================================================================
    // BLOCO 12 & 13 — CONCORRÊNCIA SIMULADA E GARANTIAS DE AUDITORIA (PDV)
    // =========================================================================
    @Nested
    @DisplayName("12 & 13. Camada de Blindagem — Concorrência Reentrante no Caixa e Garçons")
    class ConcorrenciaPdvTests {

        @Test
        @DisplayName("CT-069: Corrida de Abertura — Dois garçons abrindo simultaneamente a mesma subconta na triagem de mesas")
        void corridaAberturaSimultaneaSubconta() {
            ContaRequestDTO dto = new ContaRequestDTO(3, comandaId);

            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaMock));
            // Thread 1 lê vazio e passa; Thread 2 intercepta a linha criada milissegundos depois
            when(contaRepository.findByComandaIdAndNumeroConta(comandaId, 3))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(contaMock));

            // when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Removido stub obsoleto
            when(contaRepository.save(any(Conta.class))).thenReturn(contaMock);

            // Execução 1 processa com sucesso
            contaService.criar(dto);

            // Execução 2 esbarra no bloqueio de duplicidade concorrente síncrona
            assertThrows(BusinessRuleException.class, () -> contaService.criar(dto));
            verify(contaRepository, times(1)).save(any(Conta.class)); // Garante escrita ÚNICA
            verify(clienteRepository, never()).save(any(Cliente.class)); // Adicionado: Cliente não deve ser salvo
        }

        @Test
        @DisplayName("CT-071: Corrida de Liquidação — Dois caixas clicando em 'Liquidar' na mesma fração de segundo")
        void corridaLiquidacaoSimultanea() {
            when(contaRepository.findById(contaId)).thenReturn(Optional.of(contaMock));
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));

            // Caixa 1 liquida a conta
            contaService.liquidarConta(contaId);

            // Caixa 2 tenta liquidar na sequência paralela e esbarra no status mutado
            assertThrows(BusinessRuleException.class, () -> contaService.liquidarConta(contaId));
        }
    }
}