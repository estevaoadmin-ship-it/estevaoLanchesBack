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

        clienteMock = new Cliente();
        clienteMock.setId(clienteId);
        clienteMock.setNome("Carlos Silva");
        clienteMock.setCpf("11122233344");
        clienteMock.setEmail("carlos@email.com");
        clienteMock.setNumero("11999999999");
        clienteMock.setDataNascimento(LocalDate.of(1995, 5, 10));

        Endereco enderecoMock = new Endereco(UUID.randomUUID(), "Casa", "Rua A", "123", "", "Centro", "SP", "SP", "01000000", clienteMock);
        clienteMock.getEnderecos().add(enderecoMock);

        EnderecoRequestDTO endRequest = new EnderecoRequestDTO("Casa", "Rua A", "123", "", "Centro", "SP", "SP", "01000000");
        requestDTOMock = new ClienteRequestDTO("Carlos Silva", "11122233344", "carlos@email.com", "11999999999", LocalDate.of(1995, 5, 10), List.of(endRequest));
    }

    // --- Testes 11 e 12: Listar Todos ---
    @Test
    @DisplayName("Teste 11: Deve listar todos os clientes")
    void deveListarTodosOsClientes() {
        when(clienteRepository.findAll()).thenReturn(List.of(clienteMock));
        List<ClienteResponseDTO> resultado = clienteService.listarTodos();
        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("Teste 12: Deve retornar lista vazia quando não houver clientes")
    void deveRetornarListaVazia() {
        when(clienteRepository.findAll()).thenReturn(new ArrayList<>());
        List<ClienteResponseDTO> resultado = clienteService.listarTodos();
        assertThat(resultado).isEmpty();
    }

    // --- Testes 13 e 14: Buscar por ID ---
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

    // --- Testes 15 e 16: Buscar por CPF ---
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

    // --- Testes 17 e 18: Buscar por Nome ---
    @Test
    @DisplayName("Teste 17: Deve buscar clientes por nome")
    void deveBuscarClientesPorNome() {
        when(clienteRepository.findByNomeContainingIgnoreCase("Carlos")).thenReturn(List.of(clienteMock));
        List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("Carlos");
        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("Teste 18: Deve retornar lista vazia ao buscar nome inexistente")
    void deveRetornarListaVaziaAoBuscarNomeInexistente() {
        when(clienteRepository.findByNomeContainingIgnoreCase(any())).thenReturn(new ArrayList<>());
        List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("Inexistente");
        assertThat(resultado).isEmpty();
    }

    // --- Testes 19 a 21: Salvar Cliente ---
    @Test
    @DisplayName("Teste 19: Deve salvar cliente com sucesso")
    void deveSalvarClienteComSucesso() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
        when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any())).thenReturn(clienteMock);

        ClienteResponseDTO resultado = clienteService.salvar(requestDTOMock);
        assertThat(resultado.nome()).isEqualTo("Carlos Silva");
    }

    @Test
    @DisplayName("Teste 20: Deve lançar BusinessRuleException ao salvar CPF duplicado")
    void deveLancarExcecaoAoSalvarCpfDuplicado() {
        when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMock));
        assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
    }

    @Test
    @DisplayName("Teste 21: Deve lançar BusinessRuleException ao salvar e-mail duplicado")
    void deveLancarExcecaoAoSalvarEmailDuplicado() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
        when(clienteRepository.findByEmail("carlos@email.com")).thenReturn(Optional.of(clienteMock));
        assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
    }

    // --- Testes 22 a 25: Atualizar Cliente ---
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
    @DisplayName("Teste 24: Deve lançar BusinessRuleException ao atualizar CPF já utilizado")
    void deveLancarExcecaoAoAtualizarCpfUtilizado() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByCpfAndIdNot("11122233344", clienteId)).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> clienteService.atualizar(clienteId, requestDTOMock));
    }

    @Test
    @DisplayName("Teste 25: Deve lançar BusinessRuleException ao atualizar e-mail já utilizado")
    void deveLancarExcecaoAoAtualizarEmailUtilizado() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByCpfAndIdNot(any(), any())).thenReturn(false);
        when(clienteRepository.existsByEmailAndIdNot("carlos@email.com", clienteId)).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> clienteService.atualizar(clienteId, requestDTOMock));
    }

    // --- Testes 26 a 28: Excluir Cliente ---
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

    // --- Testes 29 a 33: Regras Internas e Endereços (Testados via fluxo de Atualização) ---
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
    @DisplayName("Teste 31: Deve remover endereços antigos ao atualizar")
    void deveRemoverEnderecosAntigos() {
        // Cliente mock já possui 1 endereço. O DTO de atualização virá com uma lista vazia.
        ClienteRequestDTO dtoVazio = new ClienteRequestDTO("Carlos Silva", "11122233344", "carlos@email.com", "11999999999", LocalDate.of(1995, 5, 10), new ArrayList<>());

        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.atualizar(clienteId, dtoVazio);
        assertThat(resultado.enderecos()).isEmpty();
    }

    @Test
    @DisplayName("Teste 32: Deve permitir atualização com lista de endereços nula")
    void devePermitirAtualizacaoComListaNula() {
        ClienteRequestDTO dtoNulo = new ClienteRequestDTO("Carlos Silva", "11122233344", "carlos@email.com", "11999999999", LocalDate.of(1995, 5, 10), null);

        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.atualizar(clienteId, dtoNulo);
        assertThat(resultado.enderecos()).isEmpty(); // Limpa a lista existente e não lança NullPointer
    }

    @Test
    @DisplayName("Teste 33: Deve garantir vínculo bidirecional entre cliente e endereço")
    void deveGarantirVinculoBidirecional() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
        when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente c = i.getArgument(0);
            // Valida o vínculo bidirecional interceptando o momento exato antes do save
            assertThat(c.getEnderecos().get(0).getCliente()).isEqualTo(c);
            return c;
        });

        clienteService.salvar(requestDTOMock);
    }
}