package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ClienteRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ClienteResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.EnderecoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.Endereco;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.DatabaseIntegrityException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteService clienteService;

    private Cliente clienteMock;
    private ClienteRequestDTO requestDTOMock;
    private UUID clienteId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();

        // Setup da Entidade Mockada
        clienteMock = new Cliente();
        clienteMock.setId(clienteId);
        clienteMock.setNome("Carlos Silva");
        clienteMock.setCpf("11122233344");
        clienteMock.setEmail("carlos@email.com");
        clienteMock.setNumero("11999999999");
        clienteMock.setDataNascimento(LocalDate.of(1995, 5, 10));

        // Setup do Endereço Bidirecional
        Endereco enderecoMock = new Endereco(UUID.randomUUID(), "Casa", "Rua A", "123", "", "Centro", "SP", "SP", "01000000", clienteMock);
        clienteMock.getEnderecos().add(enderecoMock);

        // Setup do DTO de Requisição
        EnderecoRequestDTO endRequest = new EnderecoRequestDTO("Casa", "Rua A", "123", "", "Centro", "SP", "SP", "01000000");
        requestDTOMock = new ClienteRequestDTO("Carlos Silva", "11122233344", "carlos@email.com", "11999999999", LocalDate.of(1995, 5, 10), List.of(endRequest));
    }

    // ==========================================
    // TESTES DE LISTAGEM
    // ==========================================

    @Test
    @DisplayName("Teste 11: Deve listar todos os clientes")
    void deveListarTodosOsClientes() {
        when(clienteRepository.findAll()).thenReturn(List.of(clienteMock));

        List<ClienteResponseDTO> resultado = clienteService.listarTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nome()).isEqualTo("Carlos Silva");
    }

    @Test
    @DisplayName("Teste 12: Deve retornar lista vazia quando não houver clientes")
    void deveRetornarListaVazia() {
        when(clienteRepository.findAll()).thenReturn(new ArrayList<>());

        List<ClienteResponseDTO> resultado = clienteService.listarTodos();

        assertThat(resultado).isEmpty();
    }

    // ==========================================
    // TESTES DE BUSCA POR ID E CPF
    // ==========================================

    @Test
    @DisplayName("Teste 13: Deve buscar cliente por ID existente")
    void deveBuscarClientePorIdExistente() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));

        ClienteResponseDTO resultado = clienteService.buscarPorId(clienteId);

        assertThat(resultado.nome()).isEqualTo("Carlos Silva");
    }

    @Test
    @DisplayName("Teste 14: Deve lançar exceção ao buscar ID inexistente")
    void deveLancarExcecaoAoBuscarIdInexistente() {
        when(clienteRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clienteService.buscarPorId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Teste 15: Deve buscar cliente por CPF existente")
    void deveBuscarClientePorCpfExistente() {
        when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMock));

        ClienteResponseDTO resultado = clienteService.buscarPorCpf("11122233344");

        assertThat(resultado.nome()).isEqualTo("Carlos Silva");
    }

    @Test
    @DisplayName("Teste 16: Deve lançar exceção ao buscar CPF inexistente")
    void deveLancarExcecaoAoBuscarCpfInexistente() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clienteService.buscarPorCpf("00000000000"));
    }

    // ==========================================
    // TESTES DE BUSCA POR NOME
    // ==========================================

    @Test
    @DisplayName("Teste 17: Deve buscar clientes por nome parcial")
    void deveBuscarClientesPorNome() {
        when(clienteRepository.findByNomeContainingIgnoreCase("Carlos")).thenReturn(List.of(clienteMock));

        List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("Carlos");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nome()).isEqualTo("Carlos Silva");
    }

    @Test
    @DisplayName("Teste 18: Deve retornar lista vazia ao buscar nome inexistente")
    void deveRetornarListaVaziaAoBuscarNomeInexistente() {
        when(clienteRepository.findByNomeContainingIgnoreCase(any())).thenReturn(new ArrayList<>());

        List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("Inexistente");

        assertThat(resultado).isEmpty();
    }

    // ==========================================
    // TESTES DE INSERÇÃO (SALVAR)
    // ==========================================

    @Test
    @DisplayName("Teste 19: Deve salvar cliente com sucesso")
    void deveSalvarClienteComSucesso() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
        when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any())).thenReturn(clienteMock);

        ClienteResponseDTO resultado = clienteService.salvar(requestDTOMock);

        assertThat(resultado.nome()).isEqualTo("Carlos Silva");
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Teste 20: Deve lançar BusinessRuleException ao salvar CPF duplicado")
    void deveLancarExcecaoAoSalvarCpfDuplicado() {
        when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMock));

        assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Teste 21: Deve lançar BusinessRuleException ao salvar e-mail duplicado")
    void deveLancarExcecaoAoSalvarEmailDuplicado() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
        when(clienteRepository.findByEmail("carlos@email.com")).thenReturn(Optional.of(clienteMock));

        assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    // ==========================================
    // TESTES DE ATUALIZAÇÃO (PUT)
    // ==========================================

    @Test
    @DisplayName("Teste 22: Deve atualizar cliente com sucesso")
    void deveAtualizarClienteComSucesso() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByCpfAndIdNot(any(), any())).thenReturn(false);
        when(clienteRepository.existsByEmailAndIdNot(any(), any())).thenReturn(false);
        when(clienteRepository.save(any())).thenReturn(clienteMock);

        ClienteResponseDTO resultado = clienteService.atualizar(clienteId, requestDTOMock);

        assertThat(resultado.nome()).isEqualTo("Carlos Silva");
    }

    @Test
    @DisplayName("Teste 23: Deve lançar ResourceNotFound ao atualizar cliente inexistente")
    void deveLancarExcecaoAoAtualizarInexistente() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clienteService.atualizar(clienteId, requestDTOMock));
    }

    @Test
    @DisplayName("Teste 24: Deve lançar BusinessRuleException ao atualizar CPF já utilizado por outro")
    void deveLancarExcecaoAoAtualizarCpfUtilizado() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByCpfAndIdNot("11122233344", clienteId)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> clienteService.atualizar(clienteId, requestDTOMock));
    }

    @Test
    @DisplayName("Teste 25: Deve lançar BusinessRuleException ao atualizar e-mail já utilizado por outro")
    void deveLancarExcecaoAoAtualizarEmailUtilizado() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByCpfAndIdNot(any(), any())).thenReturn(false);
        when(clienteRepository.existsByEmailAndIdNot("carlos@email.com", clienteId)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> clienteService.atualizar(clienteId, requestDTOMock));
    }

    // ==========================================
    // TESTES DE EXCLUSÃO (DELETE)
    // ==========================================

    @Test
    @DisplayName("Teste 26: Deve excluir cliente existente")
    void deveExcluirClienteExistente() {
        when(clienteRepository.existsById(clienteId)).thenReturn(true);
        doNothing().when(clienteRepository).deleteById(clienteId);

        clienteService.excluir(clienteId);

        verify(clienteRepository, times(1)).deleteById(clienteId);
    }

    @Test
    @DisplayName("Teste 27: Deve lançar exceção ao excluir cliente inexistente")
    void deveLancarExcecaoAoExcluirInexistente() {
        when(clienteRepository.existsById(clienteId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> clienteService.excluir(clienteId));
    }

    @Test
    @DisplayName("Teste 28: Deve lançar DatabaseIntegrityException ao excluir cliente vinculado a pedidos")
    void deveLancarExcecaoAoExcluirVinculado() {
        when(clienteRepository.existsById(clienteId)).thenReturn(true);
        doThrow(DataIntegrityViolationException.class).when(clienteRepository).deleteById(clienteId);

        assertThrows(DatabaseIntegrityException.class, () -> clienteService.excluir(clienteId));
    }

    // ==========================================
    // REGRAS INTERNAS E INTEGRIDADE DE ENDEREÇOS
    // ==========================================

    @Test
    @DisplayName("Testes 29 e 30: Deve copiar corretamente dados básicos e endereços para a entidade")
    void deveCopiarDadosBasicosEEnderecos() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.atualizar(clienteId, requestDTOMock);

        assertThat(resultado.nome()).isEqualTo(requestDTOMock.nome());
        assertThat(resultado.enderecos()).hasSize(1);
        assertThat(resultado.enderecos().get(0).logradouro()).isEqualTo("Rua A");
    }

    @Test
    @DisplayName("Teste 31: Deve remover endereços antigos ao atualizar com lista vazia")
    void deveRemoverEnderecosAntigos() {
        ClienteRequestDTO dtoVazio = new ClienteRequestDTO("Carlos Silva", "11122233344", "carlos@email.com", "11999999999", LocalDate.of(1995, 5, 10), new ArrayList<>());

        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.atualizar(clienteId, dtoVazio);

        assertThat(resultado.enderecos()).isEmpty();
    }

    @Test
    @DisplayName("Teste 32: Deve permitir atualização segura mesmo com lista de endereços nula (Proteção NullPointer)")
    void devePermitirAtualizacaoComListaNula() {
        ClienteRequestDTO dtoNulo = new ClienteRequestDTO("Carlos Silva", "11122233344", "carlos@email.com", "11999999999", LocalDate.of(1995, 5, 10), null);

        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.atualizar(clienteId, dtoNulo);

        assertThat(resultado.enderecos()).isEmpty();
    }

    @Test
    @DisplayName("Teste 33: Deve garantir vínculo bidirecional entre Cliente e Endereço")
    void deveGarantirVinculoBidirecional() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
        when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());

        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente c = i.getArgument(0);
            assertThat(c.getEnderecos().get(0).getCliente()).isEqualTo(c);
            return c;
        });

        clienteService.salvar(requestDTOMock);
    }

    // =========================================================================
    // 📱 BLINDAGEM DE APPS: REGRAS DE NULIDADE ADAPTADAS AO MOBILE REAL
    // =========================================================================

    @Test
    @DisplayName("Teste 34: Deve salvar cliente sem validar CPF se ele for nulo")
    void deveSalvarClienteSemValidarCpfSeNulo() {
        ClienteRequestDTO dtoSemCpf = new ClienteRequestDTO("Carlos Google", null, "google@email.com", "11999999999", null, null);

        when(clienteRepository.findByEmail("google@email.com")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteMock);

        clienteService.salvar(dtoSemCpf);

        verify(clienteRepository, never()).findByCpf(any());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Teste 35: Deve salvar cliente sem validar e-mail se ele for nulo ou vazio")
    void deveSalvarClienteSemValidarEmailSeVazio() {
        ClienteRequestDTO dtoSemEmail = new ClienteRequestDTO("Carlos Zap", "11122233344", "   ", "11999999999", null, null);

        when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteMock);

        clienteService.salvar(dtoSemEmail);

        verify(clienteRepository, never()).findByEmail(any());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Teste 36: Deve atualizar cliente sem validar CPF se ele for nulo")
    void deveAtualizarClienteSemValidarCpfSeNulo() {
        ClienteRequestDTO dtoSemCpf = new ClienteRequestDTO("Carlos Editado", null, "google@email.com", "11999999999", null, null);

        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByEmailAndIdNot("google@email.com", clienteId)).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteMock);

        clienteService.atualizar(clienteId, dtoSemCpf);

        verify(clienteRepository, never()).existsByCpfAndIdNot(any(), any());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Teste 37: Deve atualizar cliente sem validar e-mail se ele for vazio")
    void deveAtualizarClienteSemValidarEmailSeVazio() {
        ClienteRequestDTO dtoSemEmail = new ClienteRequestDTO("Carlos Editado", "11122233344", "", "11999999999", null, null);

        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByCpfAndIdNot("11122233344", clienteId)).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteMock);

        clienteService.atualizar(clienteId, dtoSemEmail);

        verify(clienteRepository, never()).existsByEmailAndIdNot(any(), any());
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }
}