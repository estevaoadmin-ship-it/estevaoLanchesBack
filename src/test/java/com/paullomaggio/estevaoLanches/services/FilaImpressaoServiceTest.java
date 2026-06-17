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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
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

        @Test
        @DisplayName("Deve ignorar outros status na busca comum")
        void deveIgnorarOutrosStatus() {
            when(repository.findByStatus(StatusImpressao.PENDENTE)).thenReturn(Collections.emptyList());
            List<FilaImpressao> resultado = service.buscarPendentes();
            assertTrue(resultado.isEmpty());
            verify(repository, never()).findByStatus(StatusImpressao.IMPRESSO);
        }
    }

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
            assertTrue(ChronoUnit.SECONDS.between(itemFila.getImpressoEm(), LocalDateTime.now()) < 5);
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
        @DisplayName("Deve rejeitar transicao se o item pular direto de PENDENTE para IMPRESSO")
        void deveRejeitarSePendente() {
            itemFila.setStatus(StatusImpressao.PENDENTE);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.marcarComoImpresso(idValido));
            assertTrue(ex.getMessage().contains("deve estar em PROCESSANDO"));
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Deve lancar NoSuchElementException se o UUID nao existir")
        void deveLancarErroSeUuidNaoEncontrado() {
            when(repository.findById(idValido)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.marcarComoImpresso(idValido));
            assertThrows(NoSuchElementException.class, () -> service.alterarParaProcessando(idValido));
        }

        @Test
        @DisplayName("Nunca deve alterar metadados estruturais do pedido ou contador ao imprimir")
        void devePreservarDadosImutaveis() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            LocalDateTime dataCriacaoOriginal = itemFila.getCriadoEm();
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            service.marcarComoImpresso(idValido);

            assertEquals(pedidoMock, itemFila.getPedido());
            assertEquals(0, itemFila.getTentativas());
            assertEquals(dataCriacaoOriginal, itemFila.getCriadoEm());
        }
    }

    @Nested
    @DisplayName("4. Bloco de Integridade e Dados")
    class IntegridadeTests {

        @Test
        @DisplayName("Deve garantir que o objeto modificado e o mesmo persistido no banco via saveAndFlush")
        void deveManterMesmaReferenciaEId() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            when(repository.findById(idValido)).thenReturn(Optional.of(itemFila));

            service.marcarComoImpresso(idValido);

            verify(repository).saveAndFlush(argThat(salvo -> salvo.getId().equals(idValido) && salvo.getPedido().equals(pedidoMock)));
        }
    }

    @Nested
    @DisplayName("5. Bloco de Transacoes")
    class TransacaoTests {

        @Test
        @DisplayName("Garantir presenca da anotacao @Transactional nos metodos de mutacao")
        void devePossuirAnotacaoTransactional() throws NoSuchMethodException {
            Method metodoMarcar = FilaImpressaoService.class.getMethod("marcarComoImpresso", UUID.class);
            Method metodoProcessar = FilaImpressaoService.class.getMethod("alterarParaProcessando", UUID.class);
            Method metodoWatchdog = FilaImpressaoService.class.getMethod("verificarProcessamentosTravados");

            assertTrue(metodoMarcar.isAnnotationPresent(Transactional.class));
            assertTrue(metodoProcessar.isAnnotationPresent(Transactional.class));
            assertTrue(metodoWatchdog.isAnnotationPresent(Transactional.class));
        }
    }

    @Nested
    @DisplayName("6. Bloco do Watchdog Resiliente")
    class WatchdogTests {

        @Test
        @DisplayName("Deve recuperar registros travados em PROCESSANDO com mais de 10 minutos via saveAndFlush")
        void deveRecuperarRegistrosAntigos() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            itemFila.setUltimaTentativa(LocalDateTime.now().minusMinutes(11));

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFila));

            service.verificarProcessamentosTravados();

            assertEquals(StatusImpressao.PENDENTE, itemFila.getStatus());
            assertEquals(1, itemFila.getTentativas());
            assertNotNull(itemFila.getUltimaTentativa());
            assertEquals("Watchdog: Tempo limite esgotado em PROCESSANDO.", itemFila.getLogErro());
            verify(repository, times(1)).saveAndFlush(itemFila);
        }

        @Test
        @DisplayName("Deve ignorar registros em PROCESSANDO que sao recentes")
        void deveIgnorarProcessandoRecentes() {
            itemFila.setStatus(StatusImpressao.PROCESSANDO);
            itemFila.setUltimaTentativa(LocalDateTime.now().minusMinutes(2));

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFila));

            service.verificarProcessamentosTravados();

            assertEquals(StatusImpressao.PROCESSANDO, itemFila.getStatus());
            assertEquals(0, itemFila.getTentativas());
            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Deve continuar processamento da fila mesmo se um registro falhar catastroficamente (Try/Catch)")
        void deveSerResilienteAFalhasIndividuais() {
            FilaImpressao itemFalho = new FilaImpressao();
            itemFalho.setStatus(StatusImpressao.PROCESSANDO);
            itemFalho.setUltimaTentativa(LocalDateTime.now().minusMinutes(15));

            FilaImpressao itemSaudavel = new FilaImpressao();
            itemSaudavel.setStatus(StatusImpressao.PROCESSANDO);
            itemSaudavel.setUltimaTentativa(LocalDateTime.now().minusMinutes(15));

            when(repository.findByStatus(StatusImpressao.PROCESSANDO)).thenReturn(List.of(itemFalho, itemSaudavel));

            // Simula uma falha física (lock) apenas no 'itemFalho'
            doThrow(new RuntimeException("Database Lock Error")).when(repository).saveAndFlush(itemFalho);

            assertDoesNotThrow(() -> service.verificarProcessamentosTravados());

            // Verifica se o erro foi suprimido e registrado no log do itemFalho
            assertTrue(itemFalho.getLogErro().contains("Falha catastofrica no Watchdog"));

            // Verifica se o loop continuou e processou o itemSaudavel com sucesso
            verify(repository, times(1)).saveAndFlush(itemSaudavel);
        }
    }

    @Nested
    @DisplayName("7. Bloco de Tratamento de Erros de Infraestrutura")
    class ErrosInfraestruturaTests {

        @Test
        @DisplayName("Deve propagar RuntimeException se o banco de dados falhar na busca")
        void devePropagarErroAoBuscar() {
            when(repository.findByStatus(StatusImpressao.PENDENTE)).thenThrow(new RuntimeException("Database offline"));
            assertThrows(RuntimeException.class, () -> service.buscarPendentes());
        }
    }

    @Nested
    @DisplayName("8. Bloco de Agendamento (Scheduler)")
    class SchedulerTests {

        @Test
        @DisplayName("Deve possuir a anotacao @Scheduled configurada com intervalo de 5 minutos")
        void devePossuirAnotacaoScheduledCorreta() throws NoSuchMethodException {
            Method metodoWatchdog = FilaImpressaoService.class.getMethod("verificarProcessamentosTravados");
            Scheduled scheduled = metodoWatchdog.getAnnotation(Scheduled.class);

            assertNotNull(scheduled);
            assertEquals(300000L, scheduled.fixedRate());
        }
    }
}