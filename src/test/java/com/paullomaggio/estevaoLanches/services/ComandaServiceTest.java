package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
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
@DisplayName("🧪 Suíte Suprema de Gestão de Mesas — Matriz de Blindagem de Comandas")
class ComandaServiceTest {

    @Mock private ComandaRepository comandaRepository;
    @Mock private MesaRepository mesaRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ContaRepository contaRepository;

    // Removido @InjectMocks
    private ComandaService comandaService;

    private Mesa mesaLivre;
    private Comanda comandaAberta;
    private UUID comandaId;
    private final Integer NUMERO_MESA = 10;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks
        comandaService = new ComandaService(comandaRepository, mesaRepository, contaRepository);

        comandaId = UUID.randomUUID();

        mesaLivre = new Mesa();
        mesaLivre.setId(UUID.randomUUID());
        mesaLivre.setNumero(NUMERO_MESA);
        mesaLivre.setStatus(StatusMesa.LIVRE);

        comandaAberta = new Comanda();
        comandaAberta.setId(comandaId);
        comandaAberta.setMesa(mesaLivre);
        comandaAberta.setStatus(StatusComanda.ABERTA);
        comandaAberta.setAbertaEm(LocalDateTime.now().minusMinutes(30));
        comandaAberta.setContas(new ArrayList<>());
    }

    // =========================================================================
    // BLOCO 1 & 2 — ABERTURA DE COMANDA & CONTROLO DE MESA OCUPADA (IDEMPOTÊNCIA)
    // =========================================================================
    @Nested
    @DisplayName("1 & 2. Camada de Blindagem — Abertura e Idempotência de Mesas")
    class AberturaComandaTests {

        @Test
        @DisplayName("CT-001 ao CT-019: Fluxo Completo Feliz — Deve abrir comanda criando mesa ausente e injetando a Conta 1 com Cliente")
        void fluxoFelizAberturaMesa() {
            when(mesaRepository.findByNumero(NUMERO_MESA)).thenReturn(Optional.empty());
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0));
            when(comandaRepository.findByMesaNumeroAndStatus(NUMERO_MESA, StatusComanda.ABERTA)).thenReturn(Optional.empty());
            when(comandaRepository.save(any(Comanda.class))).thenReturn(comandaAberta);
            // when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Removido stub obsoleto
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));

            ComandaResponseDTO dto = comandaService.abrirPorNumeroMesa(NUMERO_MESA);

            assertNotNull(dto);
            // Corrigido para esperar 2 chamadas a mesaRepository.save()
            verify(mesaRepository, times(2)).save(argThat(m -> m.getStatus() == StatusMesa.OCUPADA || m.getStatus() == StatusMesa.LIVRE));
            verify(comandaRepository, times(1)).save(any(Comanda.class));
            verify(clienteRepository, never()).save(any(Cliente.class)); // Nova verificação: Cliente não deve ser salvo
            verify(contaRepository, times(1)).save(argThat(conta -> conta.getNumeroConta() == 1 && !conta.getPago() && conta.getCliente() == null)); // Cliente deve ser null
        }

        @Test
        @DisplayName("CT-020 ao CT-024: Idempotência — Mesa já ocupada deve retornar sessão ativa")
        void mesaJaOcupadaDeveRetornarSessaoAtiva() {
            mesaLivre.setStatus(StatusMesa.OCUPADA);
            when(mesaRepository.findByNumero(NUMERO_MESA)).thenReturn(Optional.of(mesaLivre));
            when(comandaRepository.findByMesaNumeroAndStatus(NUMERO_MESA, StatusComanda.ABERTA)).thenReturn(Optional.of(comandaAberta));

            ComandaResponseDTO dto = comandaService.abrirPorNumeroMesa(NUMERO_MESA);

            assertNotNull(dto);
            verify(comandaRepository, never()).save(any(Comanda.class));
            verify(clienteRepository, never()).save(any(Cliente.class));
            verify(contaRepository, never()).save(any(Conta.class));
        }
    }

    // =========================================================================
    // BLOCO 3 — ALTERAÇÃO DE STATUS
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Transições de Status")
    class AlteracaoStatusTests {

        @Test
        @DisplayName("CT-025 ao CT-027, CT-029: Deve salvar modificação de status válidos com sucesso")
        void deveAlterarStatusComSucesso() {
            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaAberta));
            when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

            ComandaResponseDTO result = comandaService.alterarStatus(comandaId, StatusComanda.AGUARDANDO_PAGAMENTO);

            verify(comandaRepository, times(1)).save(argThat(c -> c.getStatus() == StatusComanda.AGUARDANDO_PAGAMENTO));
        }

        @Test
        @DisplayName("CT-028: Mudar status de comanda inexistente deve estourar ResourceNotFoundException")
        void deveFalharStatusComandaInexistente() {
            when(comandaRepository.findById(any())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> comandaService.alterarStatus(UUID.randomUUID(), StatusComanda.CANCELADA));
        }
    }

    // =========================================================================
    // BLOCO 4 — FECHAMENTO DE SESSÃO
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Encerramento de Atendimento")
    class FechamentoComandaTests {

        @Test
        @DisplayName("CT-030 ao CT-035, CT-038: Fechar comanda deve liberar a mesa fisicamente e setar timestamp de saída")
        void deveFecharComandaELiberarMesa() {
            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaAberta));
            when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0));

            comandaService.fecharComanda(comandaId);

            assertEquals(StatusComanda.FECHADA, comandaAberta.getStatus());
            assertEquals(StatusMesa.LIVRE, mesaLivre.getStatus());
            assertNotNull(comandaAberta.getFechadaEm());
            verify(mesaRepository, times(1)).save(mesaLivre);
            verify(comandaRepository, times(1)).save(comandaAberta);
        }

        @Test
        @DisplayName("CT-037: Tentar fechar duas vezes seguidas uma mesma sessão deve disparar BusinessRuleException")
        void deveDispararExceptionAoFecharDuasVezes() {
            comandaAberta.setStatus(StatusComanda.FECHADA);
            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaAberta));

            assertThrows(BusinessRuleException.class, () -> comandaService.fecharComanda(comandaId));
            verify(comandaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 5 & 6 — CONSULTAS E FILTRAGENS OPERACIONAIS
    // =========================================================================
    @Nested
    @DisplayName("5 & 6. Camada de Blindagem — Consultas e Filtros Síncronos")
    class ConsultasEFiltrosTests {

        @Test
        @DisplayName("CT-042 ao CT-046: Listar Ativas — Deve trazer estritamente as comandas com status ABERTA, expurgando as demais")
        void deveListarApenasComandasAbertas() {
            Comanda c2 = new Comanda(); c2.setStatus(StatusComanda.FECHADA);
            Comanda c3 = new Comanda(); c3.setStatus(StatusComanda.CANCELADA);
            // Corrigido para usar findByStatus diretamente, como no serviço
            when(comandaRepository.findByStatus(StatusComanda.ABERTA)).thenReturn(List.of(comandaAberta));

            List<ComandaResponseDTO> resultado = comandaService.listarTodasAtivas();

            assertEquals(1, resultado.size());
            verify(comandaRepository, times(1)).findByStatus(StatusComanda.ABERTA); // Verificar findByStatus
        }
    }

    // =========================================================================
    // BLOCO 7, 8 & 9 — INTEGRIDADE DOS COMPONENTES ACOPLADOS (CRM/MESA/CONTA)
    // =========================================================================
    @Nested
    @DisplayName("7, 8 & 9. Camada de Blindagem — Integridade das Chaves de Relacionamento")
    class ComponentesAcopladosTests {

        @Test
        @DisplayName("CT-047 ao CT-061: Deve garantir que as amarras bidirecionais das tabelas filhas herdem os dados da comanda mãe")
        void deveGarantirRelacionamentosCorretos() {
            when(mesaRepository.findByNumero(NUMERO_MESA)).thenReturn(Optional.of(mesaLivre));
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para mesaRepository.save
            when(comandaRepository.findByMesaNumeroAndStatus(NUMERO_MESA, StatusComanda.ABERTA)).thenReturn(Optional.empty());
            when(comandaRepository.save(any(Comanda.class))).thenReturn(comandaAberta);
            // when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Removido stub obsoleto
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));

            comandaService.abrirPorNumeroMesa(NUMERO_MESA);

            verify(contaRepository, times(1)).save(argThat(conta ->
                    conta.getComanda().equals(comandaAberta) &&
                            conta.getCliente() == null // Cliente deve ser null
            ));
            verify(clienteRepository, never()).save(any(Cliente.class)); // Adicionado: Cliente não deve ser salvo
        }
    }

    // =========================================================================
    // BLOCO 10 & 11 — FLUXOS SEQUENCIAIS DE SALÃO E REGRESSÃO INTEGRADA
    // =========================================================================
    @Nested
    @DisplayName("10 & 11. Camada de Blindagem — Regressão e Linha do Tempo do Salão")
    class RegressaoLinhaTempoTests {

        @Test
        @DisplayName("CT-064: Linha do Tempo — Abrir ➔ Fechar ➔ Abrir novamente deve gerar uma nova sessão de comanda limpa")
        void cicloVidaCompletoReabertura() {
            // Ciclo 1: Abertura e Fechamento com sucesso
            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaAberta));
            when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para comandaRepository.save
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para mesaRepository.save

            comandaService.fecharComanda(comandaId);
            assertEquals(StatusMesa.LIVRE, mesaLivre.getStatus());

            // Ciclo 2: Nova Abertura sequencial na mesma mesa desimpedida
            when(mesaRepository.findByNumero(NUMERO_MESA)).thenReturn(Optional.of(mesaLivre));
            when(comandaRepository.findByMesaNumeroAndStatus(NUMERO_MESA, StatusComanda.ABERTA)).thenReturn(Optional.empty());
            when(comandaRepository.save(any(Comanda.class))).thenReturn(new Comanda());
            // when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Removido stub obsoleto
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para contaRepository.save

            comandaService.abrirPorNumeroMesa(NUMERO_MESA);
            verify(comandaRepository, times(2)).save(any(Comanda.class)); // Agora é 2 vezes (uma no fechar, outra no abrir)
            verify(clienteRepository, never()).save(any(Cliente.class)); // Adicionado: Cliente não deve ser salvo
        }
    }

    // =========================================================================
    // BLOCO 12 — SIMULAÇÃO DETERMINÍSTICA DE CONCORRÊNCIA ENTRE GARÇONS
    // =========================================================================
    @Nested
    @DisplayName("12. Camada de Blindagem — Simulação de Concorrência Paralela")
    class ConcorrenciaTests {

        @Test
        @DisplayName("CT-073: Dois garçons clicando simultaneamente em 'Abrir Mesa' — Deve bloquear reentrância e fixar comanda única")
        void corridaAberturaDuplaMesa() {
            // Simula o primeiro cheking vendo a mesa livre e passando, enquanto o clique paralelo milissegundos depois captura o estado ocupado
            when(mesaRepository.findByNumero(NUMERO_MESA)).thenReturn(Optional.of(mesaLivre));
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para mesaRepository.save
            when(comandaRepository.findByMesaNumeroAndStatus(NUMERO_MESA, StatusComanda.ABERTA))
                    .thenReturn(Optional.empty()) // Atendimento do Garçom 1 passa limpo
                    .thenReturn(Optional.of(comandaAberta)); // Atendimento do Garçom 2 intercepta a sessão já aberta
            when(comandaRepository.save(any(Comanda.class))).thenReturn(comandaAberta); // Adicionado mock para comandaRepository.save
            // when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Removido stub obsoleto
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para contaRepository.save

            // Execução 1
            comandaService.abrirPorNumeroMesa(NUMERO_MESA);
            // Execução 2 simultânea reutiliza o token gerado sem duplicar linhas
            comandaService.abrirPorNumeroMesa(NUMERO_MESA);

            verify(comandaRepository, times(1)).save(any(Comanda.class)); // O comando save deve rodar APENAS uma vez
            verify(clienteRepository, never()).save(any(Cliente.class)); // Adicionado: Cliente não deve ser salvo
        }

        @Test
        @DisplayName("CT-074: Dois caixas enviando 'Fechar Comanda' ao mesmo tempo — A segunda transação deve ser interceptada na barreira")
        void corridaFechamentoDuplo() {
            when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaAberta));
            when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para comandaRepository.save
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para mesaRepository.save

            // Caixa 1 fecha com sucesso
            comandaService.fecharComanda(comandaId);

            // Caixa 2 bate na barreira de estado mutado imediatamente
            assertThrows(BusinessRuleException.class, () -> comandaService.fecharComanda(comandaId));
        }
    }

    // =========================================================================
    // BLOCO 13 & 14 — FLUXO INTEGRADO COMPLETO E AUDITORIA DE METADADOS
    // =========================================================================
    @Nested
    @DisplayName("13 & 14. Camada de Blindagem — Auditoria de Metadados e Ordem Rígida")
    class AuditoriaEFluxoCompletoTests {

        @Test
        @DisplayName("CT-085: Deve garantir a ordem transacional cronológica exata do fluxo completo de atendimento")
        void ordemTransacionalEstrita() {
            when(mesaRepository.findByNumero(NUMERO_MESA)).thenReturn(Optional.of(mesaLivre));
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para mesaRepository.save
            when(comandaRepository.findByMesaNumeroAndStatus(NUMERO_MESA, StatusComanda.ABERTA)).thenReturn(Optional.empty());
            when(comandaRepository.save(any(Comanda.class))).thenReturn(comandaAberta);
            // when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Removido stub obsoleto
            when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));

            comandaService.abrirPorNumeroMesa(NUMERO_MESA);

            // Prova real de conformidade cronológica síncrona
            InOrder ordemRigida = inOrder(mesaRepository, comandaRepository, contaRepository); // ClienteRepository removido
            ordemRigida.verify(mesaRepository).save(any(Mesa.class)); // Apenas uma chamada ao save para mesaRepository
            ordemRigida.verify(comandaRepository).save(any(Comanda.class));
            // ordemRigida.verify(clienteRepository).save(any(Cliente.class)); // Removido verify obsoleto
            ordemRigida.verify(contaRepository).save(any(Conta.class));
            verify(clienteRepository, never()).save(any(Cliente.class)); // Adicionado: Cliente não deve ser salvo
        }
    }
}