package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao.StatusImpressao;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.FilaImpressaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilaImpressaoServiceTest {

    @Mock
    private FilaImpressaoRepository repository;

    @InjectMocks
    private FilaImpressaoService service;

    private UUID idValido;
    private FilaImpressao itemFila;
    private Pedido pedidoMock;

    @BeforeEach
    void setUp() {
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
    // 1. BLOCO DE BUSCA DE PENDENTES
    // =========================================================================
    @Nested
    @DisplayName("1. Bloco de Busca de Pendentes")
    class BuscarPendentesTests {

        @Test
        @DisplayName("Deve retornar apenas registros com status PENDENTE")
        void deveRetornarApenasStatusPendente() {
            when(repository.findByStatus(StatusImpressao.PENDENTE)).thenReturn(List.of(itemFila));
            List<FilaImpressao> resultado = service.buscarPendentes();
            assertFalse(resultado.isEmpty());
            assertEquals(StatusImpressao.PENDENTE, resultado.get(0).getStatus());
            verify(repository, times(1)).findByStatus(StatusImpressao.PENDENTE);
        }

        @Test
        @DisplayName("Deve retornar lista vazia quando nao houver pendentes")
        void deveRetornarVazio() {
            when(repository.findByStatus(StatusImpressao.PENDENTE)).thenReturn(Collections.emptyList());
            List<FilaImpressao> resultado = service.buscarPendentes();
            assertTrue(resultado.isEmpty());
        }
    }

    // =========================================================================
    // 2. BLOCO DA MÁQUINA DE ESTADOS: PROCESSAMENTO
    // =========================================================================
    @Nested
    @DisplayName("2. Bloco da Maquina de Estados: Processamento")
    class AlterarParaProcessandoTests {

        @Test
        @DisplayName("Deve alterar item PENDENTE para PROCESSANDO com sucesso e disparar saveAndFlush")
        void deveAlterarParaProcessandoComSucesso() {
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            service.alterarParaProcessando(idValido);

            assertEquals(StatusImpressao.PROCESSANDO, itemFila.getStatus());
            assertNotNull(itemFila.getUltimaTentativa());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("Deve rejeitar alteracao para PROCESSANDO se o item ja estiver IMPRESSO")
        void deveRejeitarSeJaImpresso() {
            itemFila.setStatus(StatusImpressao.IMPRESSO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.alterarParaProcessando(idValido));
            assertTrue(ex.getMessage().contains("Nao e possivel reprocessar um item ja impresso"));
            verify(repository, never()).saveAndFlush(any());
        }
    }

    // =========================================================================
    // 3. BLOCO DA MÁQUINA DE ESTADOS: CONCLUSÃO DO HARDWARE
    // =========================================================================
    @Nested
    @DisplayName("3. Bloco da Maquina de Estados: Conclusao")
    class MarcarComoImpressoTests {

        @Test
        @DisplayName("Deve marcar como IMPRESSO se o status atual for PROCESSANDO e usar saveAndFlush")
        void deveConcluirImpressaoComSucesso() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            service.marcarComoImpresso(idValido);

            assertEquals(StatusImpressao.IMPRESSO, itemFila.getStatus());
            assertNotNull(itemFila.getImpressoEm());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("Deve rejeitar transicao se o item ja estiver IMPRESSO")
        void deveRejeitarSeJaImpresso() {
            itemFila.setStatus(StatusImpressao.IMPRESSO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.marcarComoImpresso(idValido));
            assertTrue(ex.getMessage().contains("ja consta como IMPRESSO"));
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Deve rejeitar transicao se a Bridge pular de PENDENTE direto para IMPRESSO")
        void deveRejeitarSePendente() {
            itemFila.setStatus(StatusImpressao.PENDENTE);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.marcarComoImpresso(idValido));
            assertTrue(ex.getMessage().contains("deve estar em PROCESSANDO"));
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Deve lancar NoSuchElementException se o UUID solicitado nao existir")
        void deveLancarErroSeUuidNaoEncontrado() {
            when(repository.findById(idValido)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.marcarComoImpresso(idValido));
            assertThrows(NoSuchElementException.class, () -> service.alterarParaProcessando(idValido));
        }
    }

    // =========================================================================
    // 4. BLOCO DA MÁQUINA DE ESTADOS: ROLLBACK DE BATERIA/PANE FÍSICA
    // =========================================================================
    @Nested
    @DisplayName("4. Bloco de Rollback de Hardware (Reverter)")
    class ReverterParaPendenteTests {

        @Test
        @DisplayName("Deve reverter item PROCESSANDO de volta para PENDENTE com sucesso")
        void deveReverterParaPendenteComSucesso() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            service.reverterParaPendente(idValido);

            assertEquals(StatusImpressao.PENDENTE, itemFila.getStatus());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("Deve impedir reversao se o item ja tiver sido consolidado como IMPRESSO")
        void deveImpedirReversaoDeItemJaImpresso() {
            itemFila.setStatus(StatusImpressao.IMPRESSO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.reverterParaPendente(idValido));
            assertTrue(ex.getMessage().contains("Nao e possivel reverter um item que ja foi impresso"));
            verify(repository, never()).saveAndFlush(any());
        }
    }

    // =========================================================================
    // 5. BLOCO DO WATCHDOG RESILIENTE
    // =========================================================================
    @Nested
    @DisplayName("5. Bloco do Watchdog Resiliente")
    class WatchdogTests {

        @Test
        @DisplayName("Deve recuperar e resetar registros travados em PROCESSANDO com mais de 10 minutos para PENDENTE")
        void deveRecuperarRegistrosAntigos() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            itemFila.setUltimaTentativa(LocalDateTime.now().minusMinutes(11));

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFila));

            service.verificarProcessamentosTravados();

            assertEquals(StatusImpressao.PENDENTE, itemFila.getStatus());
            assertEquals(1, itemFila.getTentativas());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("Deve manter o processamento da fila mesmo se um registro isolado falhar catastroficamente")
        void deveSerResilienteAFalhasIndividuais() {
            FilaImpressao itemFalho = new FilaImpressao();
            itemFalho.setStatus(StatusImpressao.PROCESSANDO);
            itemFalho.setUltimaTentativa(LocalDateTime.now().minusMinutes(15));

            FilaImpressao itemSaudavel = new FilaImpressao();
            itemSaudavel.setStatus(StatusImpressao.PROCESSANDO);
            itemSaudavel.setUltimaTentativa(LocalDateTime.now().minusMinutes(15));

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFalho, itemSaudavel));
            doThrow(new RuntimeException("Database Lock Error")).when(repository).saveAndFlush(itemFalho);

            assertDoesNotThrow(() -> service.verificarProcessamentosTravados());
            assertThat(itemFalho.getLogErro()).contains("Falha catastrofica no Watchdog");
            verify(repository, times(1)).saveAndFlush(itemSaudavel);
        }
    }

    // =========================================================================
    // 6. BLOCO DE REGRAS DE TRANSAÇÃO E CONTRATOS
    // =========================================================================
    @Nested
    @DisplayName("6. Bloco de Infraestrutura e Contratos")
    class TransacaoTests {

        @Test
        @DisplayName("Garantir presenca obrigatoria da anotacao @Transactional nos metodos de escrita")
        void devePossuirAnotacaoTransactional() throws NoSuchMethodException {
            Method m1 = FilaImpressaoService.class.getMethod("marcarComoImpresso", UUID.class);
            Method m2 = FilaImpressaoService.class.getMethod("alterarParaProcessando", UUID.class);
            Method m3 = FilaImpressaoService.class.getMethod("reverterParaPendente", UUID.class);

            assertTrue(m1.isAnnotationPresent(Transactional.class));
            assertTrue(m2.isAnnotationPresent(Transactional.class));
            assertTrue(m3.isAnnotationPresent(Transactional.class));
        }

        @Test
        @DisplayName("Deve possuir a anotacao @Scheduled configurada com intervalo de 5 minutos")
        void devePossuirAnotacaoScheduledCorreta() throws NoSuchMethodException {
            Method metodoWatchdog = FilaImpressaoService.class.getMethod("verificarProcessamentosTravados");
            Scheduled scheduled = metodoWatchdog.getAnnotation(Scheduled.class);

            assertNotNull(scheduled);
            assertEquals(300000L, scheduled.fixedRate());
        }
    }

    // =========================================================================
    // 7. BLOCO DE BLINDAGEM DE PARÂMETROS EXTREMOS (DEFENSIVE PROGRAMMING)
    // =========================================================================
    @Nested
    @DisplayName("7. Bloco de Blindagem e Parametros Criticos")
    class BlindagemExtremaTests {

        @Test
        @DisplayName("Deve lancar IllegalArgumentException se o UUID enviado for nulo")
        void deveRejeitarIdNuloNosMetodosDeMutacao() {
            assertThrows(IllegalArgumentException.class, () -> service.alterarParaProcessando(null));
            assertThrows(IllegalArgumentException.class, () -> service.marcarComoImpresso(null));
            assertThrows(IllegalArgumentException.class, () -> service.reverterParaPendente(null));

            verify(repository, never()).findById(any());
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Deve assegurar a sequencia logica exata de transicao da Fila via InOrder")
        void deveGarantirSequenciaLogicaDeTransicao() {
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            service.alterarParaProcessando(idValido);

            InOrder orderVerifier = inOrder(repository);
            orderVerifier.verify(repository).findById(idValido);
            orderVerifier.verify(repository).saveAndFlush(itemFila);
        }
    }
}