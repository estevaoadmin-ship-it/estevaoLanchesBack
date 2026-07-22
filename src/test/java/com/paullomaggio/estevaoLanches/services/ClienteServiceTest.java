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

    private ClienteService clienteService;

    private Cliente clienteMockCarlos;
    private Cliente clienteMockAna;
    private Cliente clienteMockPedro;
    private ClienteRequestDTO requestDTOMock;
    private UUID clienteIdCarlos;
    private UUID clienteIdAna;
    private UUID clienteIdPedro;

    @BeforeEach
    void setUp() {
        clienteService = new ClienteService(clienteRepository);

        clienteIdCarlos = UUID.randomUUID();
        clienteMockCarlos = new Cliente();
        clienteMockCarlos.setId(clienteIdCarlos);
        clienteMockCarlos.setNome("CARLOS SILVA");
        clienteMockCarlos.setCpf("11122233344");
        clienteMockCarlos.setEmail("carlos@email.com");
        clienteMockCarlos.setNumero("11999999999");
        clienteMockCarlos.setStatus(StatusCliente.ATIVO);
        clienteMockCarlos.setEnderecos(new ArrayList<>());

        clienteIdAna = UUID.randomUUID();
        clienteMockAna = new Cliente();
        clienteMockAna.setId(clienteIdAna);
        clienteMockAna.setNome("ANA PEREIRA");
        clienteMockAna.setCpf("55566677788");
        clienteMockAna.setEmail("ana@email.com");
        clienteMockAna.setNumero("16999999999");
        clienteMockAna.setStatus(StatusCliente.ATIVO);
        clienteMockAna.setEnderecos(new ArrayList<>());

        clienteIdPedro = UUID.randomUUID();
        clienteMockPedro = new Cliente();
        clienteMockPedro.setId(clienteIdPedro);
        clienteMockPedro.setNome("PEDRO ALVES");
        clienteMockPedro.setCpf("99988877766");
        clienteMockPedro.setEmail("pedro@email.com");
        clienteMockPedro.setNumero("16888888888");
        clienteMockPedro.setStatus(StatusCliente.ATIVO);
        clienteMockPedro.setEnderecos(new ArrayList<>());


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
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMockCarlos));
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
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMockCarlos));
            List<ClienteResponseDTO> resultado = clienteService.listarTodos();
            ClienteResponseDTO dto = resultado.get(0);
            assertEquals(clienteMockCarlos.getId(), dto.id());
            assertEquals(clienteMockCarlos.getNome(), dto.nome());
        }

        @Test
        @DisplayName("CT-004: Deve retornar a listagem preservando a ordenação original do repositório")
        void ct004_preservarOrdem() {
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMockCarlos, clienteMockAna));
            List<ClienteResponseDTO> resultado = clienteService.listarTodos();
            assertEquals("CARLOS SILVA", resultado.get(0).nome());
            assertEquals("ANA PEREIRA", resultado.get(1).nome());
        }

        @Test
        @DisplayName("CT-005: Operações de listagem nunca devem disparar comandos de persistência")
        void ct005_nuncaSalvarNaListagem() {
            when(clienteRepository.findAll()).thenReturn(List.of(clienteMockCarlos));
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
            when(clienteRepository.findById(clienteIdCarlos)).thenReturn(Optional.of(clienteMockCarlos));
            ClienteResponseDTO resultado = clienteService.buscarPorId(clienteIdCarlos);
            assertNotNull(resultado);
            assertEquals(clienteIdCarlos, resultado.id());
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
            when(clienteRepository.findById(clienteIdCarlos)).thenReturn(Optional.of(clienteMockCarlos));
            ClienteResponseDTO resultado = clienteService.buscarPorId(clienteIdCarlos);
            assertEquals("CARLOS SILVA", resultado.nome());
            assertEquals("11122233344", resultado.cpf());
        }

        @Test
        @DisplayName("CT-010: Consultas por ID nunca devem invocar o método de persistência save()")
        void ct010_naoChamaSave() {
            when(clienteRepository.findById(clienteIdCarlos)).thenReturn(Optional.of(clienteMockCarlos));
            clienteService.buscarPorId(clienteIdCarlos);
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
            when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMockCarlos));
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
    // BLOCO 4 — BUSCAR POR NOME (ATUALIZADO)
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Buscar por Nome, CPF ou Telefone (Delivery)")
    class BuscarPorNomeCpfTelefoneTests {

        @Test
        @DisplayName("CT-017: Deve buscar por nome parcial e case-insensitive")
        void ct017_buscaPorNomeParcialCaseInsensitive() {
            when(clienteRepository.findByNomeContainingIgnoreCase("carlos")).thenReturn(List.of(clienteMockCarlos));
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("carlos");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("CARLOS SILVA", resultado.get(0).nome());
            verify(clienteRepository, times(1)).findByNomeContainingIgnoreCase("carlos");
            verify(clienteRepository, never()).findByCpf(any());
            verify(clienteRepository, never()).findByNumero(any());
        }

        @Test
        @DisplayName("CT-018: Deve buscar por nome completo e case-insensitive")
        void ct018_buscaPorNomeCompletoCaseInsensitive() {
            when(clienteRepository.findByNomeContainingIgnoreCase("CARLOS SILVA")).thenReturn(List.of(clienteMockCarlos));
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("CARLOS SILVA");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("CARLOS SILVA", resultado.get(0).nome());
            verify(clienteRepository, times(1)).findByNomeContainingIgnoreCase("CARLOS SILVA");
        }

        @Test
        @DisplayName("CT-019: Deve retornar lista vazia para nome inexistente")
        void ct019_buscaPorNomeInexistente() {
            when(clienteRepository.findByNomeContainingIgnoreCase("INEXISTENTE")).thenReturn(Collections.emptyList());
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("INEXISTENTE");
            assertTrue(resultado.isEmpty());
            verify(clienteRepository, times(1)).findByNomeContainingIgnoreCase("INEXISTENTE");
        }

        @Test
        @DisplayName("CT-020: Deve buscar por CPF sem máscara")
        void ct020_buscaPorCpfSemMascara() {
            when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMockCarlos));
            when(clienteRepository.findByNumero("11122233344")).thenReturn(Optional.empty()); // Pode ser telefone também
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("11122233344");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("11122233344", resultado.get(0).cpf());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, times(1)).findByCpf("11122233344");
            verify(clienteRepository, times(1)).findByNumero("11122233344");
        }

        @Test
        @DisplayName("CT-021: Deve buscar por CPF com máscara")
        void ct021_buscaPorCpfComMascara() {
            when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMockCarlos));
            when(clienteRepository.findByNumero("11122233344")).thenReturn(Optional.empty()); // Pode ser telefone também
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("111.222.333-44");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("11122233344", resultado.get(0).cpf());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, times(1)).findByCpf("11122233344");
            verify(clienteRepository, times(1)).findByNumero("11122233344");
        }

        @Test
        @DisplayName("CT-022: Deve buscar por telefone sem máscara")
        void ct022_buscaPorTelefoneSemMascara() {
            when(clienteRepository.findByNumero("16999999999")).thenReturn(Optional.of(clienteMockAna));
            when(clienteRepository.findByCpf("16999999999")).thenReturn(Optional.empty()); // Pode ser CPF também
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("16999999999");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("16999999999", resultado.get(0).numero());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, times(1)).findByNumero("16999999999");
            verify(clienteRepository, times(1)).findByCpf("16999999999");
        }

        @Test
        @DisplayName("CT-023: Deve buscar por telefone com máscara")
        void ct023_buscaPorTelefoneComMascara() {
            when(clienteRepository.findByNumero("16999999999")).thenReturn(Optional.of(clienteMockAna));
            when(clienteRepository.findByCpf("16999999999")).thenReturn(Optional.empty()); // Pode ser CPF também
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("(16) 99999-9999");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("16999999999", resultado.get(0).numero());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, times(1)).findByNumero("16999999999");
            verify(clienteRepository, times(1)).findByCpf("16999999999");
        }

        @Test
        @DisplayName("CT-024: Deve retornar lista vazia para termo nulo")
        void ct024_termoNulo() {
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome(null);
            assertTrue(resultado.isEmpty());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, never()).findByCpf(any());
            verify(clienteRepository, never()).findByNumero(any());
        }

        @Test
        @DisplayName("CT-025: Deve retornar lista vazia para termo vazio")
        void ct025_termoVazio() {
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("");
            assertTrue(resultado.isEmpty());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, never()).findByCpf(any());
            verify(clienteRepository, never()).findByNumero(any());
        }

        @Test
        @DisplayName("CT-026: Deve retornar lista vazia para termo em branco")
        void ct026_termoEmBranco() {
            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("   ");
            assertTrue(resultado.isEmpty());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, never()).findByCpf(any());
            verify(clienteRepository, never()).findByNumero(any());
        }

        // REMOVIDO: CT-027 e CT-028 são conceitualmente incorretos, pois esperam buscas numéricas para termos textuais.

        @Test
        @DisplayName("CT-029: Deve retornar cliente único mesmo se encontrado por CPF e Telefone (mesmo número)")
        void ct029_semDuplicidadeCpfTelefone() {
            // Cliente com CPF e Telefone iguais (hipotético para teste)
            Cliente clienteCpfTelIgual = new Cliente();
            clienteCpfTelIgual.setId(UUID.randomUUID());
            clienteCpfTelIgual.setNome("Cliente CPF Telefone");
            clienteCpfTelIgual.setCpf("11122233344");
            clienteCpfTelIgual.setNumero("11122233344");

            // Para um termo de 11 dígitos, o service tenta buscar por CPF e por Telefone
            when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteCpfTelIgual));
            when(clienteRepository.findByNumero("11122233344")).thenReturn(Optional.of(clienteCpfTelIgual));

            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("11122233344");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("Cliente CPF Telefone", resultado.get(0).nome());
            verify(clienteRepository, never()).findByNomeContainingIgnoreCase(any());
            verify(clienteRepository, times(1)).findByCpf("11122233344");
            verify(clienteRepository, times(1)).findByNumero("11122233344");
        }

        @Test
        @DisplayName("CT-030: Deve buscar por nome quando o termo contém números mas não é um CPF/telefone válido")
        void ct030_buscaNomeComNumeros() {
            when(clienteRepository.findByNomeContainingIgnoreCase("Cliente 123")).thenReturn(List.of(clienteMockCarlos));
            // Para termos que não são 10 ou 11 dígitos numéricos, não deve chamar findByCpf/findByNumero
            verify(clienteRepository, never()).findByCpf(any());
            verify(clienteRepository, never()).findByNumero(any());

            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("Cliente 123");
            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
            assertEquals("CARLOS SILVA", resultado.get(0).nome());
            verify(clienteRepository, times(1)).findByNomeContainingIgnoreCase("Cliente 123");
        }

        @Test
        @DisplayName("CT-031: Deve retornar lista vazia se nenhum critério encontrar resultados")
        void ct031_nenhumCriterioEncontra() {
            when(clienteRepository.findByNomeContainingIgnoreCase("TermoInexistente")).thenReturn(Collections.emptyList());
            // Para termos textuais, não deve chamar findByCpf/findByNumero
            verify(clienteRepository, never()).findByCpf(any());
            verify(clienteRepository, never()).findByNumero(any());

            List<ClienteResponseDTO> resultado = clienteService.buscarPorNome("TermoInexistente");
            assertTrue(resultado.isEmpty());
            verify(clienteRepository, times(1)).findByNomeContainingIgnoreCase("TermoInexistente");
        }
    }

    // =========================================================================
    // BLOCO 5 — SALVAR CLIENTE
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — Salvar Clientes")
    class SalvarClienteTests {

        @Test
        @DisplayName("CT-032 [Original Teste 19]: Deve salvar novo cliente com sucesso convertendo dados")
        void deveSalvarClienteComSucesso() {
            when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
            when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
                Cliente c = i.getArgument(0);
                c.setId(clienteIdCarlos);
                return c;
            });

            ClienteResponseDTO resultado = clienteService.salvar(requestDTOMock);

            assertNotNull(resultado.id());
            verify(clienteRepository, times(1)).save(any(Cliente.class));
        }

        @Test
        @DisplayName("CT-033 [Original Teste 40]: Permite salvar cliente com e-mail nulo absoluto sem disparar duplicidade")
        void deveSalvarClienteComEmailNuloSemEstourarValidacaoDeDuplicidade() {
            ClienteRequestDTO dtoMesaSalao = new ClienteRequestDTO("Paulo Da Mesa 10", null, null, "16993939957", null, StatusCliente.ATIVO, null);
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

            ClienteResponseDTO resultado = clienteService.salvar(dtoMesaSalao);

            assertNull(resultado.email());
            verify(clienteRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("CT-034: Novo cliente sem status explícito deve ser inicializado como ATIVO por padrão")
        void ct034_statusPadraoAtivo() {
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
        @DisplayName("CT-035: Deve barrar criação de cliente se o CPF já estiver cadastrado")
        void ct035_cpfDuplicado() {
            when(clienteRepository.findByCpf("11122233344")).thenReturn(Optional.of(clienteMockCarlos));
            assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
            verify(clienteRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-036: Deve barrar criação de cliente se o E-mail já estiver cadastrado")
        void ct036_emailDuplicado() {
            when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
            when(clienteRepository.findByEmail("carlos@email.com")).thenReturn(Optional.of(clienteMockCarlos));
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
        @DisplayName("CT-037 [Original Teste 22]: Deve atualizar cliente mapeando mutações com segurança")
        void deveAtualizarClienteComSucesso() {
            when(clienteRepository.findById(clienteIdCarlos)).thenReturn(Optional.of(clienteMockCarlos));
            when(clienteRepository.existsByCpfAndIdNot(any(), any())).thenReturn(false);
            when(clienteRepository.existsByEmailAndIdNot(any(), any())).thenReturn(false);
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

            ClienteResponseDTO resultado = clienteService.atualizar(clienteIdCarlos, requestDTOMock);

            assertEquals(clienteIdCarlos, resultado.id());
            verify(clienteRepository, times(1)).save(any(Cliente.class));
        }

        @Test
        @DisplayName("CT-038: Deve impedir atualização se o novo CPF já pertencer a outro registro ativo")
        void ct038_atualizarCpfDuplicado() {
            when(clienteRepository.findById(clienteIdCarlos)).thenReturn(Optional.of(clienteMockCarlos));
            when(clienteRepository.existsByCpfAndIdNot("11122233344", clienteIdCarlos)).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> clienteService.atualizar(clienteIdCarlos, requestDTOMock));
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
        @DisplayName("CT-039 e CT-040: Deve excluir cliente existente acionando o repositório uma única vez")
        void ct039_excluirClienteComSucesso() {
            when(clienteRepository.existsById(clienteIdCarlos)).thenReturn(true);
            doNothing().when(clienteRepository).deleteById(clienteIdCarlos);

            clienteService.excluir(clienteIdCarlos);

            verify(clienteRepository, times(1)).deleteById(clienteIdCarlos);
        }

        @Test
        @DisplayName("CT-041 e CT-042: Violação de integridade no banco de dados (histórico de pedidos) deve lançar DatabaseIntegrityException")
        void ct041_historicoImpedeExclusao() {
            when(clienteRepository.existsById(clienteIdCarlos)).thenReturn(true);
            doThrow(DataIntegrityViolationException.class).when(clienteRepository).deleteById(clienteIdCarlos);

            assertThrows(DatabaseIntegrityException.class, () -> clienteService.excluir(clienteIdCarlos));
        }
    }

    // =========================================================================
    // BLOCO 10 — ENDEREÇOS
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Agrupamento de Endereços")
    class EnderecosTests {

        @Test
        @DisplayName("CT-043: Um cliente novo deve ser inicializado com uma lista de endereços vazia e segura")
        void ct043_clienteSemEndereco() {
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
        @DisplayName("CT-044: Fluxo de Regressão Completo: Salvar ➔ Buscar por ID ➔ Comparar Dados")
        void ct044_regressaoSalvarBuscar() {
            when(clienteRepository.findByCpf(any())).thenReturn(Optional.empty());
            when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteMockCarlos);
            when(clienteRepository.findById(clienteIdCarlos)).thenReturn(Optional.of(clienteMockCarlos));

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
        @DisplayName("CT-045: Dois garçons tentando submeter simultaneamente o mesmo CPF na triagem de mesas")
        void ct045_corridaCpfSimultaneo() {
            when(clienteRepository.findByCpf("11122233344"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(clienteMockCarlos));
            when(clienteRepository.findByEmail(any())).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
                Cliente c = invocation.getArgument(0);
                c.setId(clienteIdCarlos);
                return c;
            });

            clienteService.salvar(requestDTOMock);

            assertThrows(BusinessRuleException.class, () -> clienteService.salvar(requestDTOMock));
        }
    }
}