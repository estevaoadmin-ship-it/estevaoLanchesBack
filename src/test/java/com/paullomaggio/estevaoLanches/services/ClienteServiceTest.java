package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ClienteRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ClienteResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.enums.StatusCliente;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("🧪 Suíte de Testes — Gestão de Consumidores (Service)")
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
        clienteMock.setStatus(StatusCliente.ATIVO);
        clienteMock.setEnderecos(new ArrayList<>());

        requestDTOMock = new ClienteRequestDTO("Carlos Silva", "11122233344", "carlos@email.com", "11999999999", LocalDate.of(1995, 5, 10), StatusCliente.ATIVO, new ArrayList<>());
    }

    @Test
    @DisplayName("Teste 11: Deve listar todos os clientes")
    void deveListarTodosOsClientes() {
        when(clienteRepository.findAll()).thenReturn(List.of(clienteMock));
        List<ClienteResponseDTO> resultado = clienteService.listarTodos();
        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("Teste 19: Deve salvar cliente com sucesso padronizando caixa alta")
    void deveSalvarClienteComSucesso() {
        when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
        when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.salvar(requestDTOMock);

        assertThat(resultado.nome()).isEqualTo("CARLOS SILVA");
    }

    @Test
    @DisplayName("Teste 22: Deve atualizar cliente com sucesso convertendo o nome para MAIÚSCULO")
    void deveAtualizarClienteComSucesso() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
        when(clienteRepository.existsByCpfAndIdNot(any(), any())).thenReturn(false);
        when(clienteRepository.existsByEmailAndIdNot(any(), any())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.atualizar(clienteId, requestDTOMock);

        assertThat(resultado.nome()).isEqualTo("CARLOS SILVA");
    }

    @Test
    @DisplayName("Teste 40: Deve permitir salvar cliente com e-mail nulo absoluto sem disparar validação de duplicidade")
    void deveSalvarClienteComEmailNuloSemEstourarValidacaoDeDuplicidade() {
        ClienteRequestDTO dtoMesaSalão = new ClienteRequestDTO("Paulo Da Mesa 10", null, null, "16993939957", null, StatusCliente.ATIVO, null);

        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resultado = clienteService.salvar(dtoMesaSalão);

        assertThat(resultado.email()).isNull();
        verify(clienteRepository, never()).findByEmail(any());
    }
}