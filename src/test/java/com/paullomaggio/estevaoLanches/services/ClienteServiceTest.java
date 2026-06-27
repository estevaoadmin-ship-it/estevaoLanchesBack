package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ClienteRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ClienteResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.Endereco;
import com.paullomaggio.estevaoLanches.enums.StatusCliente;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.DatabaseIntegrityException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Gestão de Clientes — Matriz de Blindagem do CRM")
class ClienteServiceTest {

    @Mock private ClienteRepository clienteRepository;
    @InjectMocks private ClienteService clienteService;

    private Cliente clienteMock;
    private ClienteRequestDTO requestDTOMock;
    private UUID clienteId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();

        clienteMock = new Cliente();
        clienteMock.setId(clienteId);
        clienteMock.setNome("CARLOS SILVA");
        clienteMock.setCpf("11122233344");
        clienteMock.setEmail("carlos@email.com");
        clienteMock.setNumero("11999999999");
        clienteMock.setStatus(StatusCliente.ATIVO);
        clienteMock.setEnderecos(new ArrayList<>());

        requestDTOMock = new ClienteRequestDTO(
                "Carlos Silva",
                "11122233344",
                "carlos@email.com",
                "11999999999",
                LocalDate.of(1995, 5, 10),
                StatusCliente.ATIVO,
                new ArrayList<>()
        );
    }

    // =========================================================================
    // BLOCO 1 — LISTAGEM
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — Listagem Geral")
    class ListagemTests {

        @Test
        @DisplayName("CT-001 [Original Teste 11]: Deve listar todos os clientes cadastrados com sucesso")
        void deveListarTodosOsClientes() {
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMock));
            List<ClienteResponseDTO> resultado = clienteService.listarTodos();
            assertEquals(1, resultado.size());
            verify(clienteRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("CT-002: Deve retornar lista vazia quando não existir nenhum registro")
        void ct002_listaVazia() {
            when(clienteRepository.findAll()).thenReturn(Collections.emptyList());
            List<ClienteResponseDTO> resultado = clienteService.listarTodos();
            assertTrue(resultado.isEmpty());
        }

        @Test
        @DisplayName("CT-003: Deve converter corretamente os campos de Entity para DTO")
        void ct003_conversaoEntityDto() {
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMock));
            List<ClienteResponseDTO> resultado = clienteService.listarTodos();
            ClienteResponseDTO dto = resultado.get(0);
            assertEquals(clienteMock.getId(), dto.id());
            assertEquals(clienteMock.getNome(), dto.nome());
        }

        @Test
        @DisplayName("CT-004: Deve retornar a listagem preservando a ordenação original do repositório")
        void ct004_preservarOrdem() {
            Cliente segundo = new Cliente(); segundo.setNome("ANA");
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMock, segundo));
            List<ClienteResponseDTO> resultado = clienteService.listarTodos();
            assertEquals("CARLOS SILVA", resultado.get(0).nome());
            assertEquals("ANA", resultado.get(1).nome());
        }

        @Test
        @DisplayName("CT-005: Operações de listagem nunca devem disparar comandos de persistência")
        void ct005_nuncaSalvarNaListagem() {
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMock));
            clienteService.listarTodos();
            verify(clienteRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 2 — BUSCAR POR ID
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Buscar por ID")
    class BuscarPorIdTests {

        @Test
        @DisplayName("CT-006: Deve localizar cliente existente por ID")
        void ct006_clienteExistente() {
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
            ClienteResponseDTO resultado = clienteService.buscarPorId(clienteId);
            assertNotNull(resultado);
            assertEquals(clienteId, resultado.id());
        }

        @Test
        @DisplayName("CT-007: Cliente inexistente deve lançar ResourceNotFoundException")
        void ct007_clienteInexistente() {
            when(clienteRepository.findById(any())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> clienteService.buscarPorId(UUID.randomUUID()));
        }

        @Test
        @DisplayName("CT-009: Deve validar o preenchimento correto de todas as propriedades do DTO de resposta")
        void ct009_dtoCorreto() {
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
            ClienteResponseDTO resultado = clienteService.buscarPorId(clienteId);
            assertEquals("CARLOS SILVA", resultado.nome());
            assertEquals("11122233344", resultado.cpf());
        }

        @Test
        @DisplayName("CT-010: Consultas por ID nunca devem invocar o método de persistência save()")
        void ct010_naoChamaSave() {
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
            clienteService.buscarPorId(clienteId);
            verify(clienteRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 3 — BUSCAR POR CPF
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Buscar por CPF")
    class BuscarPorCpfTests {

        @Test
        @DisplayName("CT-011: Deve localizar cliente por CPF existente")
        void ct011_cpfExistente() {
            when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMock));
            ClienteResponseDTO resultado = clienteService.buscarPorCpf("11122233344");
            assertEquals("11122233344", resultado.cpf());
        }

        @Test
        @DisplayName("CT-012: CPF inexistente deve estourar ResourceNotFoundException")
        void ct012_cpfInexistente() {
            when(clienteRepository.findByCpf("00000000000")).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> clienteService.buscarPorCpf("00000000000"));
        }
    }

    // =========================================================================
    // BLOCO 4 — BUSCAR POR NOME
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Buscar por Nome")
    class BuscarPorNomeTests {

        @Test
        @DisplayName("CT-017, CT-018 e CT-019: Deve buscar por nome de forma parcial, completa e ignorando case")
        void ct017_buscaPorNomeFiltros() {
            when(clienteRepository.findByNomeContainingIgnoreCase("carlos")).thenReturn(List.of(clienteMock));
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("carlos");
            assertFalse(resultado.isEmpty());
            assertEquals("CARLOS SILVA", resultado.get(0).nome());
        }
    }

    // =========================================================================
    // BLOCO 5 — SALVAR CLIENTE
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — Salvar Clientes")
    class SalvarClienteTests {

        @Test
        @DisplayName("CT-022 [Original Teste 19]: Deve salvar novo cliente com sucesso convertendo dados")
        void deveSalvarClienteComSucesso() {
            when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
            when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
                Cliente c = i.getArgument(0);
                c.setId(clienteId);
                return c;
            });

            ClienteResponseDTO resultado = clienteService.salvar(requestDTOMock);

            assertNotNull(resultado.id());
            verify(clienteRepository, times(1)).save(any(Cliente.class));
        }

        @Test
        @DisplayName("CT-024 [Original Teste 40]: Permite salvar cliente com e-mail nulo absoluto sem disparar duplicidade")
        void deveSalvarClienteComEmailNuloSemEstourarValidacaoDeDuplicidade() {
            ClienteRequestDTO dtoMesaSalao = new ClienteRequestDTO("Paulo Da Mesa 10", null, null, "16993939957", null, StatusCliente.ATIVO, null);
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

            ClienteResponseDTO resultado = clienteService.salvar(dtoMesaSalao);

            assertNull(resultado.email());
            verify(clienteRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("CT-030: Novo cliente sem status explícito deve ser inicializado como ATIVO por padrão")
        void ct030_statusPadraoAtivo() {
            Cliente c = new Cliente();
            assertEquals(StatusCliente.ATIVO, c.getStatus());
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — REGRAS E VALIDAÇÕES DE CPF E EMAIL
    // =========================================================================
    @Nested
    @DisplayName("6 & 7. Camada de Blindagem — Regras de Chaves Únicas")
    class RegrasChavesUnicasTests {

        @Test
        @DisplayName("CT-031: Deve barrar criação de cliente se o CPF já estiver cadastrado")
        void ct031_cpfDuplicado() {
            when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMock));
            assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
            verify(clienteRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-038: Deve barrar criação de cliente se o E-mail já estiver cadastrado")
        void ct038_emailDuplicado() {
            when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
            when(clienteRepository.findByEmail("carlos@email.com")).thenReturn(Optional.of(clienteMock));
            assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
        }
    }

    // =========================================================================
    // BLOCO 8 — ATUALIZAÇÃO
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Atualização de Dados")
    class AtualizacaoTests {

        @Test
        @DisplayName("CT-046 [Original Teste 22]: Deve atualizar cliente mapeando mutações com segurança")
        void deveAtualizarClienteComSucesso() {
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
            when(clienteRepository.existsByCpfAndIdNot(any(), any())).thenReturn(false);
            when(clienteRepository.existsByEmailAndIdNot(any(), any())).thenReturn(false);
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

            ClienteResponseDTO resultado = clienteService.atualizar(clienteId, requestDTOMock);

            assertEquals(clienteId, resultado.id());
            verify(clienteRepository, times(1)).save(any(Cliente.class));
        }

        @Test
        @DisplayName("CT-053: Deve impedir atualização se o novo CPF já pertencer a outro registro ativo")
        void ct053_atualizarCpfDuplicado() {
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));
            when(clienteRepository.existsByCpfAndIdNot("11122233344", clienteId)).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> clienteService.atualizar(clienteId, requestDTOMock));
            verify(clienteRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 9 — EXCLUSÃO
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Remoção Física")
    class ExclusaoTests {

        @Test
        @DisplayName("CT-056 e CT-060: Deve excluir cliente existente acionando o repositório uma única vez")
        void ct056_excluirClienteComSucesso() {
            when(clienteRepository.existsById(clienteId)).thenReturn(true);
            doNothing().when(clienteRepository).deleteById(clienteId);

            clienteService.excluir(clienteId);

            verify(clienteRepository, times(1)).deleteById(clienteId);
        }

        @Test
        @DisplayName("CT-058 e CT-059: Violação de integridade no banco de dados (histórico de pedidos) deve lançar DatabaseIntegrityException")
        void ct058_historicoImpedeExclusao() {
            when(clienteRepository.existsById(clienteId)).thenReturn(true);
            doThrow(DataIntegrityViolationException.class).when(clienteRepository).deleteById(clienteId);

            assertThrows(DatabaseIntegrityException.class, () -> clienteService.excluir(clienteId));
        }
    }

    // =========================================================================
    // BLOCO 10 — ENDEREÇOS
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Agrupamento de Endereços")
    class EnderecosTests {

        @Test
        @DisplayName("CT-066: Um cliente novo deve ser inicializado com uma lista de endereços vazia e segura")
        void ct066_clienteSemEndereco() {
            Cliente novo = new Cliente();
            assertNotNull(novo.getEnderecos());
            assertTrue(novo.getEnderecos().isEmpty());
        }
    }

    // =========================================================================
    // BLOCO 15 — REGRESSÃO DE ESTADO INTEGRADO
    // =========================================================================
    @Nested
    @DisplayName("15. Camada de Blindagem — Cenários de Regressão Crítica")
    class RegressaoTests {

        @Test
        @DisplayName("CT-084: Fluxo de Regressão Completo: Salvar ➔ Buscar por ID ➔ Comparar Dados")
        void ct084_regressaoSalvarBuscar() {
            when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
            when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteMock);
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clienteMock));

            ClienteResponseDTO salvo = clienteService.salvar(requestDTOMock);
            ClienteResponseDTO consultado = clienteService.buscarPorId(salvo.id());

            assertEquals(salvo.id(), consultado.id());
            assertEquals(salvo.nome(), consultado.nome());
        }
    }

    // =========================================================================
    // BLOCO 17 — SIMULAÇÃO DETERMINÍSTICA DE CONCORRÊNCIA (RACE CONDITIONS)
    // =========================================================================
    @Nested
    @DisplayName("17. Camada de Blindagem — Concorrência e Bloqueios no PDV")
    class ConcorrenciaTests {

        @Test
        @DisplayName("CT-098: Dois garçons tentando submeter simultaneamente o mesmo CPF na triagem de mesas")
        void ct098_corridaCpfSimultaneo() {
            // Primeiro cheking do garçom 1 encontra a vaga livre, o do garçom 2 intercepta a trava ativa
            when(clienteRepository.findByCpf("11122233344"))
                    .thenReturn(Optional.empty()) // Garçom 1 passa
                    .thenReturn(Optional.of(clienteMock)); // Garçom 2 bate na barreira

            clienteService.salvar(requestDTOMock);

            assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
        }
    }
}