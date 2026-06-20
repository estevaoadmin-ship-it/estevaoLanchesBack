package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.entities.Subconta;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import com.paullomaggio.estevaoLanches.repositories.ComandaRepository;
import com.paullomaggio.estevaoLanches.repositories.MesaRepository;
import com.paullomaggio.estevaoLanches.repositories.SubcontaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map; // 🚀 INCLUÍDO: Necessário para a validação do novo formato JSON
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComandaControllerTest {

    @Mock private MesaRepository mesaRepository;
    @Mock private ComandaRepository comandaRepository;
    @Mock private SubcontaRepository subcontaRepository;

    @InjectMocks
    private ComandaController comandaController;

    private UUID empresaIdEsperado;
    private UUID filialIdEsperado;
    private Mesa mesaMockExistente;

    @BeforeEach
    void setUp() {
        empresaIdEsperado = UUID.fromString("11111111-1111-1111-1111-111111111111");
        filialIdEsperado = UUID.fromString("22222222-2222-2222-2222-222222222222");

        mesaMockExistente = new Mesa();
        mesaMockExistente.setId(UUID.randomUUID());
        mesaMockExistente.setNumero(8);
        mesaMockExistente.setStatus(StatusMesa.LIVRE);
        mesaMockExistente.setEmpresaId(empresaIdEsperado);
        mesaMockExistente.setFilialId(filialIdEsperado);
    }

    // ==========================================
    // 1. PRIORIDADE 1: ESSENCIAIS (MÉTODO PRINCIPAL)
    // ==========================================

    @Test
    @DisplayName("Teste 1: Deve criar uma nova mesa com propriedades e IDs mockados corretos se ela não existir")
    void deveCriarNovaMesaQuandoNaoExistir() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.empty());
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());

        when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0));
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        ArgumentCaptor<Mesa> mesaCaptor = ArgumentCaptor.forClass(Mesa.class);
        verify(mesaRepository, times(2)).save(mesaCaptor.capture());

        Mesa novaMesaCriada = mesaCaptor.getAllValues().get(0);
        assertThat(novaMesaCriada.getNumero()).isEqualTo(8);
        assertThat(novaMesaCriada.getEmpresaId()).isEqualTo(empresaIdEsperado);
        assertThat(novaMesaCriada.getFilialId()).isEqualTo(filialIdEsperado);
    }

    @Test
    @DisplayName("Teste 2: Deve reutilizar a instância de mesa existente do PostgreSQL")
    void deveReutilizarMesaExistente() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        verify(mesaRepository, times(1)).save(mesaMockExistente);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Teste 3: Deve retornar a comanda existente (HTTP 200) sem criar novos registros se houver comanda aberta")
    void deveRetornarComandaExistenteCasoJaExistaUmaAberta() {
        Comanda comandaAtivaMock = new Comanda();
        comandaAtivaMock.setId(UUID.randomUUID());
        comandaAtivaMock.setStatus(StatusComanda.ABERTA);
        comandaAtivaMock.setMesa(mesaMockExistente);

        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.of(comandaAtivaMock));

        ResponseEntity<?> response = comandaController.abrirComanda(8);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 🚀 CORRIGIDO: Cast do corpo da resposta para o formato real Map mapeado no controller
        Map<String, Object> resultadoMap = (Map<String, Object>) response.getBody();
        assertThat(resultadoMap).isNotNull();
        assertThat(resultadoMap.get("id")).isEqualTo(comandaAtivaMock.getId());
        assertThat(resultadoMap.get("status")).isEqualTo(comandaAtivaMock.getStatus());
        assertThat(resultadoMap.get("idJaExistia")).isEqualTo(true); // Garante que a trava do mobile continue funcionando

        verify(comandaRepository, never()).save(any(Comanda.class));
        verify(subcontaRepository, never()).save(any(Subconta.class));
        assertThat(mesaMockExistente.getStatus()).isEqualTo(StatusMesa.LIVRE);
    }

    @Test
    @DisplayName("Teste 4: Deve abrir uma nova comanda populando os metadados obrigatórios")
    void deveAbrirNovaComanda() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        ArgumentCaptor<Comanda> comandaCaptor = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository, times(1)).save(comandaCaptor.capture());

        Comanda novaComanda = comandaCaptor.getValue();
        assertThat(novaComanda.getStatus()).isEqualTo(StatusComanda.ABERTA);
        assertThat(novaComanda.getAbertaEm()).isNotNull();
        assertThat(novaComanda.getMesa()).isEqualTo(mesaMockExistente);
    }

    @Test
    @DisplayName("Teste 5: Deve criar automaticamente a Subconta Pai (Conta 1) vinculada à comanda")
    void deveCriarAoMenosUmaSubcontaPai() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        ArgumentCaptor<Subconta> subcontaCaptor = ArgumentCaptor.forClass(Subconta.class);
        verify(subcontaRepository, times(1)).save(subcontaCaptor.capture());

        Subconta subcontaCriada = subcontaCaptor.getValue();
        assertThat(subcontaCriada.getNumeroConta()).isEqualTo(1);
        assertThat(subcontaCriada.getPago()).isFalse();
    }

    // ==========================================
    // 2. PRIORIDADE 2: SEQUENCIAMENTO DE FLUXO
    // ==========================================

    @Test
    @DisplayName("Teste 6: Deve salvar a mesa antes da comanda (Integridade de Chave Estrangeira)")
    void deveSalvarMesaAntesDaComanda() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        InOrder order = inOrder(mesaRepository, comandaRepository);
        order.verify(mesaRepository).save(any(Mesa.class));
        order.verify(comandaRepository).save(any(Comanda.class));
    }

    @Test
    @DisplayName("Teste 7: Deve salvar a comanda antes da subconta (Garante o ID gerado na subconta)")
    void deveSalvarComandaAntesDaSubconta() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        InOrder order = inOrder(comandaRepository, subcontaRepository);
        order.verify(comandaRepository).save(any(Comanda.class));
        order.verify(subcontaRepository).save(any(Subconta.class));
    }

    @Test
    @DisplayName("Teste 8: Deve alterar status da mesa para OCUPADA impossibilitando garçons duplicados")
    void deveAlterarStatusDaMesaParaOcupada() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());

        comandaController.abrirComanda(8);

        assertThat(mesaMockExistente.getStatus()).isEqualTo(StatusMesa.OCUPADA);
    }

    @Test
    @DisplayName("Teste 9: Deve preencher a data e hora exata da abertura da comanda")
    void devePreencherDataDeAbertura() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        ArgumentCaptor<Comanda> comandaCaptor = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(comandaCaptor.capture());
        assertThat(comandaCaptor.getValue().getAbertaEm()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    // ==========================================
    // 3. PRIORIDADE 3: UUID E SEGURANÇA MULTI-EMPRESA
    // ==========================================

    @Test
    @DisplayName("Teste 10 e 11: Deve injetar com segurança os UUIDs mockados de Empresa e Filial na Comanda")
    void devePreencherEmpresaEFilialIdNaComanda() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        ArgumentCaptor<Comanda> comandaCaptor = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(comandaCaptor.capture());

        assertThat(comandaCaptor.getValue().getEmpresaId()).isEqualTo(empresaIdEsperado);
        assertThat(comandaCaptor.getValue().getFilialId()).isEqualTo(filialIdEsperado);
    }

    @Test
    @DisplayName("Teste 12 e 13: Deve propagar os UUIDs mockados de Empresa e Filial também na Mesa criada")
    void devePreencherEmpresaEFilialIdTambemNaMesaCriada() {
        when(mesaRepository.findByNumero(99)).thenReturn(Optional.empty());
        when(comandaRepository.findByMesaNumeroAndStatus(99, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(99);

        ArgumentCaptor<Mesa> mesaCaptor = ArgumentCaptor.forClass(Mesa.class);
        verify(mesaRepository, atLeastOnce()).save(mesaCaptor.capture());

        assertThat(mesaCaptor.getValue().getEmpresaId()).isEqualTo(empresaIdEsperado);
        assertThat(mesaCaptor.getValue().getFilialId()).isEqualTo(filialIdEsperado);
    }

    // ==========================================
    // 4. PRIORIDADE 4: CASOS EXTREMOS
    // ==========================================

    @Test
    @DisplayName("Teste 14: Deve garantir isolamento total abrindo comandas diferentes para mesas diferentes")
    void deveAbrirComandasDiferentesParaMesasDiferentes() {
        Mesa mesa12 = new Mesa(UUID.randomUUID(), empresaIdEsperado, filialIdEsperado, 12, StatusMesa.LIVRE);
        Mesa mesa14 = new Mesa(UUID.randomUUID(), empresaIdEsperado, filialIdEsperado, 14, StatusMesa.LIVRE);

        when(mesaRepository.findByNumero(12)).thenReturn(Optional.of(mesa12));
        when(mesaRepository.findByNumero(14)).thenReturn(Optional.of(mesa14));

        comandaController.abrirComanda(12);
        comandaController.abrirComanda(14);

        verify(comandaRepository, times(2)).save(any(Comanda.class));
    }

    @Test
    @DisplayName("Teste 15: Deve permitir abrir uma nova comanda caso as anteriores daquela mesa já estejam FECHADAS")
    void devePermitirAbrirNovaComandaQuandoAnteriorEstiverFechada() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        verify(comandaRepository, times(1)).save(any(Comanda.class));
    }

    @Test
    @DisplayName("Teste 16: Deve disparar o save de subconta estritamente uma única vez por fluxo")
    void deveCriarApenasUmaSubcontaAoAbrir() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());

        comandaController.abrirComanda(8);

        verify(subcontaRepository, times(1)).save(any(Subconta.class));
    }

    @Test
    @DisplayName("Teste 17: Deve retornar exatamente a instância da comanda processada e salva")
    void deveRetornarExatamenteAComandaSalva() {
        Comanda comandaSalvaNoPostgres = new Comanda();
        comandaSalvaNoPostgres.setId(UUID.randomUUID());

        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenReturn(comandaSalvaNoPostgres);

        ResponseEntity<?> response = comandaController.abrirComanda(8);

        assertEquals(comandaSalvaNoPostgres, response.getBody());
    }

    @Test
    @DisplayName("Teste 18: Deve salvar o registro da mesa duas vezes se ela for nova (persistência + atualização)")
    void deveSalvarMesaCriadaApenasInstanciaCorreta() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.empty());
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());

        when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0));
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(8);

        verify(mesaRepository, times(2)).save(any(Mesa.class));
    }

    @Test
    @DisplayName("Teste 19: Não deve tentar criar ou registrar nova mesa caso ela já exista")
    void naoDeveCriarNovaMesaQuandoElaJaExiste() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());

        comandaController.abrirComanda(8);

        ArgumentCaptor<Mesa> captor = ArgumentCaptor.forClass(Mesa.class);
        verify(mesaRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusMesa.OCUPADA);
    }

    @Test
    @DisplayName("Teste 20: Deve mapear e amarrar a comanda exatamente ao número enviado no path param")
    void deveManterNumeroCorretoDaMesa() {
        Mesa mesa12 = new Mesa(UUID.randomUUID(), empresaIdEsperado, filialIdEsperado, 12, StatusMesa.LIVRE);
        when(mesaRepository.findByNumero(12)).thenReturn(Optional.of(mesa12));
        when(comandaRepository.findByMesaNumeroAndStatus(12, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenAnswer(i -> i.getArgument(0));

        comandaController.abrirComanda(12);

        ArgumentCaptor<Comanda> comandaCaptor = ArgumentCaptor.forClass(Comanda.class);
        verify(comandaRepository).save(comandaCaptor.capture());
        assertThat(comandaCaptor.getValue().getMesa().getNumero()).isEqualTo(12);
    }

    // ==========================================
    // 5. TESTES DE EXCEÇÃO (PROPAGAÇÃO DE FALHAS)
    // ==========================================

    @Test
    @DisplayName("Teste 21: Deve abortar o fluxo e propagar erro caso o banco de dados falhe ao salvar a mesa")
    void falhaAoSalvarMesa() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());

        when(mesaRepository.save(any(Mesa.class))).thenThrow(new RuntimeException("PostgreSQL Offline"));

        assertThrows(RuntimeException.class, () -> comandaController.abrirComanda(8));

        verify(comandaRepository, never()).save(any());
        verify(subcontaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Teste 22: Deve impedir criação de subcontas se o salvamento da comanda falhar")
    void falhaAoSalvarComanda() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(comandaRepository.save(any(Comanda.class))).thenThrow(new RuntimeException("Erro de restrição"));

        assertThrows(RuntimeException.class, () -> comandaController.abrirComanda(8));

        verify(subcontaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Teste 23: Propaga exceção se falhar ao persistir a subconta inicial")
    void falhaAoSalvarSubconta() {
        when(mesaRepository.findByNumero(8)).thenReturn(Optional.of(mesaMockExistente));
        when(comandaRepository.findByMesaNumeroAndStatus(8, StatusComanda.ABERTA)).thenReturn(Optional.empty());
        when(subcontaRepository.save(any(Subconta.class))).thenThrow(new RuntimeException("Falha FK"));

        assertThrows(RuntimeException.class, () -> comandaController.abrirComanda(8));
    }
}