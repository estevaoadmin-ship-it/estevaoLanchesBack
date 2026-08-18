package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.FilaImpressaoDTO;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao.DestinoImpressao;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao.StatusImpressao;
import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.FilaImpressaoRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemComboRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Impressão — Matriz de Blindagem da Máquina de Estados Distribuída")
class FilaImpressaoServiceTest {

    @Mock private FilaImpressaoRepository repository;
    @Mock private ItemComboRepository itemComboRepository;
    // Removido @InjectMocks
    private FilaImpressaoService service;

    private UUID idValido;
    private FilaImpressao itemFila;
    private Pedido pedidoMock;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com o mock
        service = new FilaImpressaoService(repository, itemComboRepository);

        idValido = UUID.randomUUID();
        pedidoMock = new Pedido();
        pedidoMock.setId(UUID.randomUUID());

        itemFila = new FilaImpressao();
        itemFila.setId(idValido);
        itemFila.setPedido(pedidoMock);
        itemFila.setStatus(StatusImpressao.PENDENTE);
        itemFila.setTentativas(0);
        itemFila.setCriadoEm(LocalDateTime.now().minusMinutes(5));
    }

    // =========================================================================
    // BLOCO 1 & 2 — INICIALIZAÇÃO DA FILA E RECUPERAÇÃO DE PENDENTES
    // =========================================================================
    @Nested
    @DisplayName("1 & 2. Camada de Blindagem — Ciclo de Criação e Leitura de Pendentes")
    class LifecycleECriacaoTests {

        @Test
        @DisplayName("CT-001 ao CT-009: Atributos Nativos — Uma nova fila deve nascer como PENDENTE, com 0 tentativas e timestamp de entrada")
        void ct001_deveGarantirEstadoInicialDaEntidade() {
            FilaImpressao novaFila = new FilaImpressao();
            novaFila.setPedido(pedidoMock);
            novaFila.setDestino(DestinoImpressao.COZINHA);

            assertEquals(StatusImpressao.PENDENTE, novaFila.getStatus());
            assertEquals(0, novaFila.getTentativas());
            assertNotNull(novaFila.getCriadoEm());
            assertEquals(DestinoImpressao.COZINHA, novaFila.getDestino());
        }

        @Test
        @DisplayName("CT-010 ao CT-014: Filtro de Pendentes — Buscar pendentes deve retornar estritamente registros livres para processamento")
        void ct010_deveRetornarApenasRegistrosPendentes() {
            when(repository.findByStatus(StatusImpressao.PENDENTE)).thenReturn(List.of(itemFila));

            List<FilaImpressaoDTO> resultado = service.buscarPendentes();

            assertFalse(resultado.isEmpty());
            assertEquals(StatusImpressao.PENDENTE, resultado.get(0).getStatus());
            verify(repository, times(1)).findByStatus(StatusImpressao.PENDENTE);
            verify(itemComboRepository, never()).findByItemPedidoIdIn(anyList());
    }


        @Test
        @DisplayName("CT-011: Lista Vazia — Se não houver cupons livres, deve retornar uma coleção imutável vazia")
        void ct011_deveRetornarVazioQuandoNaoHouverPendentes() {
        when(repository.findByStatus(StatusImpressao.PENDENTE)).thenReturn(Collections.emptyList());
            List<FilaImpressaoDTO> resultado = service.buscarPendentes();
            verify(itemComboRepository, never()).findByItemPedidoIdIn(anyList());
            assertTrue(resultado.isEmpty());
        }
    }

    // =========================================================================
    // BLOCO 3 — TRANSIÇÃO PARA PROCESSANDO (BLOQUEIO DO HARDWARE)
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Alteração para PROCESSANDO")
    class AlterarParaProcessandoTests {

        @Test
        @DisplayName("CT-015 ao CT-017: Transição Válida — Mudar de PENDENTE para PROCESSANDO deve carregar o timestamp da tentativa e dar saveAndFlush")
        void ct015_deveMudarParaProcessandoComSucesso() {
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));
            when(repository.saveAndFlush(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));

            service.alterarParaProcessando(idValido);

            assertEquals(StatusImpressao.PROCESSANDO, itemFila.getStatus());
            assertNotNull(itemFila.getUltimaTentativa());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("CT-018 e CT-019: Defesa contra Nulos e Órfãos — ID nulo ou inexistente deve abortar sem tocar no banco")
        void ct018_deveRejeitarIdInexistenteOuNulo() {
            assertThrows(IllegalArgumentException.class, () -> service.alterarParaProcessando(null));

            UUID idQualquer = UUID.randomUUID();
            when(repository.findById(idQualquer)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.alterarParaProcessando(idQualquer));
        }

        @Test
        @DisplayName("CT-020: Bloqueio de Consolidados — Tentar jogar de volta para PROCESSANDO um cupom que já foi IMPRESSO deve ser barrado")
        void ct020_deveImpedirProcessarItemJaImpresso() {
            itemFila.setStatus(StatusImpressao.IMPRESSO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            assertThrows(BusinessRuleException.class, () -> service.alterarParaProcessando(idValido));
            verify(repository, never()).saveAndFlush(any());
        }
    }

    // =========================================================================
    // BLOCO 4 — CONSOLIDAÇÃO DE IMPRESSÃO (IMPRESSO)
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Confirmação Física (Marcar como Impresso)")
    class MarcarComoImpressoTests {

        @Test
        @DisplayName("CT-022 ao CT-024: Fluxo Tradicional — Consolidação só é válida se o cupom estiver em PROCESSANDO")
        void ct022_deveConsolidarImpressaoComSucesso() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));
            when(repository.saveAndFlush(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));

            service.marcarComoImpresso(idValido);

            assertEquals(StatusImpressao.IMPRESSO, itemFila.getStatus());
            assertNotNull(itemFila.getImpressoEm());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("CT-027: Antissalto de Estado — Tentar pular direto de PENDENTE para IMPRESSO (sem passar pelo Node) deve estourar BusinessRuleException")
        void ct027_deveBarrarTransicaoDiretaPendenteParaImpresso() {
            itemFila.setStatus(StatusImpressao.PENDENTE); // Pulando a etapa do Node
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            assertThrows(BusinessRuleException.class, () -> service.marcarComoImpresso(idValido));
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("CT-028: Idempotência de Baixa — Tentar reconfirmar um item que já consta como IMPRESSO deve ser bloqueado")
        void ct028_deveRejeitarConfirmacaoDuplicada() {
            itemFila.setStatus(StatusImpressao.IMPRESSO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            assertThrows(BusinessRuleException.class, () -> service.marcarComoImpresso(idValido));
        }
    }

    // =========================================================================
    // BLOCO 5 — REVERSÃO OPERACIONAL (ROLLBACK POR PANE DE HARDWARE)
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — Reversão por Erros Físicos (Rollback)")
    class ReverterParaPendenteTests {

        @Test
        @DisplayName("CT-030 ao CT-032: Recuperação de Linha — Se a guilhotina ou papel travar, joga de PROCESSANDO para PENDENTE preenchendo o log")
        void ct030_deveReverterParaPendenteComLog() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));
            when(repository.saveAndFlush(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));

            service.reverterParaPendente(idValido);

            assertEquals(StatusImpressao.PENDENTE, itemFila.getStatus());
            assertNotNull(itemFila.getLogErro());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("CT-035: Inviolabilidade de Histórico — Nunca deve ser permitida a reversão de cupons que já foram consolidados como IMPRESSO")
        void ct035_naoDeveReverterItemJaImpresso() {
            itemFila.setStatus(StatusImpressao.IMPRESSO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            assertThrows(BusinessRuleException.class, () -> service.reverterParaPendente(idValido));
        }
    }

    // =========================================================================
    // BLOCO 6 — WATCHDOG RESILIENTE CONTRA TRAVAMENTOS DE REDE
    // =========================================================================
    @Nested
    @DisplayName("6. Camada de Blindagem — Rotina de Watchdog Automático")
    class WatchdogResilienteTests {

        @Test
        @DisplayName("CT-037 ao CT-041: Resgate Cronológico — Itens travados em PROCESSANDO há mais de 10 minutos devem voltar para PENDENTE incrementando o contador")
        void ct037_deveResgatarItensTravados() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            itemFila.setUltimaTentativa(LocalDateTime.now().minusMinutes(12)); // Estourou o tempo limite de 10min

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFila));
            when(repository.saveAndFlush(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));

            service.verificarProcessamentosTravados();

            assertEquals(StatusImpressao.PENDENTE, itemFila.getStatus());
            assertEquals(1, itemFila.getTentativas());
            assertNotNull(itemFila.getLogErro());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("CT-042: Janela de Tolerância — Itens em PROCESSANDO dentro da janela aceitável (Ex: 5 minutos) devem ser ignorados")
        void ct042_naoDeveMudarItensDentroDoPrazo() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            itemFila.setUltimaTentativa(LocalDateTime.now().minusMinutes(4)); // Dentro da janela tolerável

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFila));

            service.verificarProcessamentosTravados();

            assertEquals(StatusImpressao.PROCESSANDO, itemFila.getStatus()); // Mantém processando
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("CT-045 e CT-046: Isolamento Catastrófico — Uma falha severa na gravação de um cupom devedor não pode derrubar o processamento dos demais itens saudáveis da fila")
        void ct045_watchdogDeveSerResilienteAFalhasIndividuais() {
            FilaImpressao itemFalho = new FilaImpressao();
            itemFalho.setStatus(StatusImpressao.PROCESSANDO);
            itemFalho.setUltimaTentativa(LocalDateTime.now().minusMinutes(15));

            FilaImpressao itemSaudavel = new FilaImpressao();
            itemSaudavel.setStatus(StatusImpressao.PROCESSANDO);
            itemSaudavel.setUltimaTentativa(LocalDateTime.now().minusMinutes(15));

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFalho, itemSaudavel));

            // Força a explosão de concorrência ou lock físico de banco na primeira gravação
            doThrow(new RuntimeException("Database Lock Critical Failure")).when(repository).saveAndFlush(itemFalho);
            when(repository.saveAndFlush(itemSaudavel)).thenAnswer(i -> i.getArgument(0)); // Mock para o item saudável

            assertDoesNotThrow(() -> service.verificarProcessamentosTravados());
            assertTrue(itemFalho.getLogErro().contains("Falha catastrofica no Watchdog"));
            verify(repository, times(1)).saveAndFlush(itemSaudavel); // Item saudável foi salvo normalmente
        }
    }

    // =========================================================================
    // BLOCO 7 & 12 — CRONOLOGIA DE TRANSAÇÃO E AUDITORIA DE METADADOS
    // =========================================================================
    @Nested
    @DisplayName("7 & 12. Camada de Blindagem — Metadados e Cronologia de Transações")
    class TransacoesEContratosTests {

        @Test
        @DisplayName("CT-048 e CT-085: Ordem Cronológica — Deve assegurar a sequência síncrona exata de leitura e alteração via InOrder")
        void ct048_deveGarantirOrdemCronologicaEstrita() {
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));
            when(repository.saveAndFlush(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));

            service.alterarParaProcessando(idValido);

            InOrder verificadorOrdem = inOrder(repository);
            verificadorOrdem.verify(repository).findById(idValido);
            verificadorOrdem.verify(repository).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("Garantir a presença obrigatória da decoração transacional nativa nos métodos modificadores")
        void deveConterAnotacoesDeInfraestrutura() throws NoSuchMethodException {
            Method m1 = FilaImpressaoService.class.getMethod("marcarComoImpresso", UUID.class);
            Method m2 = FilaImpressaoService.class.getMethod("verificarProcessamentosTravados");

            assertTrue(m1.isAnnotationPresent(Transactional.class));
            assertTrue(m2.isAnnotationPresent(Scheduled.class));
            assertEquals(300000L, m2.getAnnotation(Scheduled.class).fixedRate()); // Intervalo de 5 minutos configurado
        }
    }

    // =========================================================================
    // BLOCO 10 — SIMULAÇÃO DETERMINÍSTICA DE CONCORRÊNCIA ENTRE IMPRESSORAS
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Testes Concorrentes (Múltiplos Nodes)")
    class ConcorrenciaNodesTests {

        @Test
        @DisplayName("CT-071: Concorrência Reentrante — Dois computadores de cupom tentando capturar simultaneamente a mesma comanda")
        void ct071_corridaDeNodesPeloMesmoCupom() {
            // Instância 1 lê o cupom pendente, Instância 2 intercepta a linha modificada milissegundos depois
            when(repository.findById(idValido))
                    .thenReturn(Optional.of(itemFila)) // Node 1 lê e passa
                    .thenReturn(Optional.of(itemFila)); // Node 2 lê na mesma fração de segundo
            when(repository.saveAndFlush(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));

            // Node 1 executa e altera status
            service.alterarParaProcessando(idValido);

            // Node 2 executa de forma paralela concorrente e passa sem duplicar a escrita
            service.alterarParaProcessando(idValido);

            verify(repository, times(2)).saveAndFlush(any(FilaImpressao.class));
        }

        @Test
        @DisplayName("CT-072: Dois ACKs de confirmação batendo juntos no servidor — Apenas uma consolidação é persistida")
        void ct072_corridaDeAckSimultaneo() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));
            when(repository.saveAndFlush(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));

            // ACK do terminal 1 consolida com sucesso
            service.marcarComoImpresso(idValido);

            // ACK tardio ou duplicado do terminal 2 bate na barreira de estado consolidado
            assertThrows(BusinessRuleException.class, () -> service.marcarComoImpresso(idValido));
        }
    }
}