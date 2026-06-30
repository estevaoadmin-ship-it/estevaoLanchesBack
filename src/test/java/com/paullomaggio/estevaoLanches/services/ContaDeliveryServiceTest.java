package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.RegistroDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Suprema — Engenharia de Matriz de Contas Digitais e CRM")
class ContaDeliveryServiceTest {

    @Mock private ContaDeliveryRepository contaDeliveryRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private PasswordEncoder passwordEncoder;

    // Removido @InjectMocks
    private ContaDeliveryService contaDeliveryService;

    private Cliente clienteExistenteMock;
    private UUID idClienteOriginal;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks
        contaDeliveryService = new ContaDeliveryService(contaDeliveryRepository, clienteRepository, passwordEncoder);

        idClienteOriginal = UUID.randomUUID();
        clienteExistenteMock = new Cliente();
        clienteExistenteMock.setId(idClienteOriginal);
        clienteExistenteMock.setNome("PAULO ORIGINAL MESA");
        clienteExistenteMock.setNumero("16993939957");
        clienteExistenteMock.setEmail("antigo@estevao.com");
        clienteExistenteMock.setEnderecos(new ArrayList<>());
    }

    // =========================================================================
    // BLOCO 1 & 7 — CADASTRO HAPPY PATH & ESTADO DA CONTA DELIVERY
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — Fluxos Felizes de Cadastro")
    class CadastroHappyPathTests {

        @Test
        @DisplayName("CT-001, CT-004, CT-008, CT-048 e CT-049: Deve criar ContaDelivery ativa e vinculada ao Cliente correto")
        void ct001_deveCadastrarNovaContaComSucesso() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Estevao Lanches", "contato@estevao.com", "16999998888", "senha123");

            when(contaDeliveryRepository.existsByEmail("contato@estevao.com")).thenReturn(false);
            when(clienteRepository.findByNumero("16999998888")).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
            when(passwordEncoder.encode("senha123")).thenReturn("hash_criptografado");
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            ArgumentCaptor<ContaDelivery> contaCaptor = ArgumentCaptor.forClass(ContaDelivery.class);
            assertDoesNotThrow(() -> contaDeliveryService.registrarNovaConta(dto));

            verify(contaDeliveryRepository, times(1)).save(contaCaptor.capture());
            ContaDelivery contaSalva = contaCaptor.getValue();

            assertTrue(contaSalva.isAtivo());
            assertNotNull(contaSalva.getCliente());
            assertEquals("contato@estevao.com", contaSalva.getEmail());
        }

        @Test
        @DisplayName("CT-002, CT-040, CT-041 e CT-042: Criptografia — Deve invocar o encoder e nunca salvar senha em texto puro")
        void ct002_deveGarantirCriptografiaDeSenha() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Estevao", "senha@test.com", "111", "senhaPura");
            when(contaDeliveryRepository.existsByEmail("senha@test.com")).thenReturn(false);
            when(clienteRepository.findByNumero("111")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("senhaPura")).thenReturn("hash_seguro_criptografado");
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            contaDeliveryService.registrarNovaConta(dto);

            verify(passwordEncoder, times(1)).encode("senhaPura");
            verify(contaDeliveryRepository).save(argThat(conta -> conta.getSenha().equals("hash_seguro_criptografado")));
        }

        @Test
        @DisplayName("CT-003 e CT-047: Permissões — Deve assinar obrigatoriamente a role padrão ROLE_CLIENTE")
        void ct003_deveAtribuirRoleCliente() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Estevao", "role@test.com", "222", "123");
            when(contaDeliveryRepository.existsByEmail("role@test.com")).thenReturn(false);
            when(clienteRepository.findByNumero("222")).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            contaDeliveryService.registrarNovaConta(dto);

            verify(contaDeliveryRepository).save(argThat(conta -> conta.getRole().equals("ROLE_CLIENTE")));
        }

        @Test
        @DisplayName("CT-005, CT-006 e CT-007: Higienização — Deve normalizar Email (lowercase/trim), Nome (uppercase) e Telefone")
        void ct005_deveHigienizarENormalizarInputs() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("  maggio lanches  ", "  Contato@ESTEVALANCHES.Com  ", "1699999-1111", "123");
            when(contaDeliveryRepository.existsByEmail("contato@estevalanches.com")).thenReturn(false);
            when(clienteRepository.findByNumero("16999991111")).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            contaDeliveryService.registrarNovaConta(dto);

            verify(clienteRepository).save(argThat(c -> c.getNome().equals("MAGGIO LANCHES")));
            verify(contaDeliveryRepository).save(argThat(conta -> conta.getEmail().equals("contato@estevalanches.com")));
        }
    }

    // =========================================================================
    // BLOCO 2 — CLIENTE COMERCIAL (ESTRATÉGIA DE ATENDIMENTO MULTICANAL)
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Identidade Comercial Única")
    class ClienteComercialTests {

        @Test
        @DisplayName("CT-010, CT-012, CT-014 e CT-015: Unificação — Se o telefone já existe no salão, vincula a ContaDelivery ao mesmo UUID")
        void ct010_deveReaproveitarClienteExistentePorTelefone() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Paulo App", "paulo@delivery.com", "16993939957", "123");
            when(contaDeliveryRepository.existsByEmail("paulo@delivery.com")).thenReturn(false);
            when(clienteRepository.findByNumero("16993939957")).thenReturn(Optional.of(clienteExistenteMock));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            contaDeliveryService.registrarNovaConta(dto);

            verify(clienteRepository, never()).save(any(Cliente.class)); // NENHUM cliente novo é criado
            verify(contaDeliveryRepository).save(argThat(conta -> conta.getCliente().getId().equals(idClienteOriginal)));
        }

        @Test
        @DisplayName("CT-013: Atualização de Ficha — Deve atualizar o e-mail do cliente físico com o novo e-mail cadastrado no App")
        void ct013_deveAtualizarEmailDoClienteExistente() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Paulo", "novo.email@delivery.com", "16993939957", "123");
            when(contaDeliveryRepository.existsByEmail("novo.email@delivery.com")).thenReturn(false);
            when(clienteRepository.findByNumero("16993939957")).thenReturn(Optional.of(clienteExistenteMock));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            contaDeliveryService.registrarNovaConta(dto);

            assertEquals("novo.email@delivery.com", clienteExistenteMock.getEmail());
        }
    }

    // =========================================================================
    // BLOCO 3 — REGRAS DE E-MAIL
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Regras de E-mail")
    class EmailValidationTests {

        @Test
        @DisplayName("CT-016: Duplicidade — Deve barrar e lançar exceção se o e-mail digital já estiver em uso")
        void ct016_deveBarrarEmailDuplicado() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("X", "duplicado@mail.com", "123", "123");
            when(contaDeliveryRepository.existsByEmail("duplicado@mail.com")).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> contaDeliveryService.registrarNovaConta(dto));
            verify(contaDeliveryRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-022 e CT-023: Nulos e Vazios — Deve barrar requisições com e-mail nulo no payload")
        void ct022_deveBarrarEmailNulo() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("X", null, "123", "123");
            assertThrows(BusinessRuleException.class, () -> contaDeliveryService.registrarNovaConta(dto));
        }
    }

    // =========================================================================
    // BLOCO 4 — REGRAS DE TELEFONE
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Regras e Máscaras de Telefone")
    class TelefoneValidationTests {

        @Test
        @DisplayName("CT-024 ao CT-029: Limpeza de String — Deve expurgar parênteses, traços, espaços e símbolos de máscara")
        void ct024_deveLimparQualquerMascaraDeTelefone() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Estevao", "tel@test.com", "+55 (16) 99999-2222", "123");
            when(contaDeliveryRepository.existsByEmail("tel@test.com")).thenReturn(false);
            when(clienteRepository.findByNumero("5516999992222")).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            contaDeliveryService.registrarNovaConta(dto);

            verify(clienteRepository).findByNumero("5516999992222");
        }

        @Test
        @DisplayName("CT-031: Ausência de Contato — Deve reter processamento se o telefone vier nulo")
        void ct031_deveBarrarTelefoneNulo() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("X", "a@a.com", null, "123");
            assertThrows(BusinessRuleException.class, () -> contaDeliveryService.registrarNovaConta(dto));
        }
    }

    // =========================================================================
    // BLOCO 8 — SEGURANÇA E HIGIENIZAÇÃO (XSS / INJEÇÃO)
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Barreiras de Segurança de Inputs")
    class SecurityInputsTests {

        @Test
        @DisplayName("CT-051 ao CT-054: Injeção de Scripts — Tags HTML e comandos devem ser gravados como strings literais sem quebrar o fluxo")
        void ct051_deveTratarTagsHtmlComoStringsLiterais() {
            String nomeComScript = "Admin <script>alert('hack')</script>";
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO(nomeComScript, "xss@test.com", "999", "123");

            when(contaDeliveryRepository.existsByEmail("xss@test.com")).thenReturn(false);
            when(clienteRepository.findByNumero("999")).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            assertDoesNotThrow(() -> contaDeliveryService.registrarNovaConta(dto));
            verify(clienteRepository).save(argThat(c -> c.getNome().equals("ADMIN <SCRIPT>ALERT('HACK')</SCRIPT>")));
        }
    }

    // =========================================================================
    // BLOCO 9 — PERSISTÊNCIA E ORDEM TRANSACIONAL
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Sequenciamento e Ordem Transacional")
    class TransactionalOrderingTests {

        @Test
        @DisplayName("CT-056 e CT-057: Cronologia Rígida — O Cliente (Comercial) DEVE ser persistido obrigatoriamente antes do registro de acesso (ContaDelivery)")
        void ct056_deveSeguirOrdemRigidaDePersistencia() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Ordem", "ordem@test.com", "888", "123");

            when(contaDeliveryRepository.existsByEmail("ordem@test.com")).thenReturn(false);
            when(clienteRepository.findByNumero("888")).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteExistenteMock);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            contaDeliveryService.registrarNovaConta(dto);

            // Prova real cronológica por InOrder
            InOrder ordemTransacional = inOrder(clienteRepository, contaDeliveryRepository);
            ordemTransacional.verify(clienteRepository).findByNumero("888");
            ordemTransacional.verify(clienteRepository).save(any(Cliente.class));
            ordemTransacional.verify(contaDeliveryRepository).save(any(ContaDelivery.class));
        }
    }

    // =========================================================================
    // BLOCO 11 — CONCORRÊNCIA SIMULADA
    // =========================================================================
    @Nested
    @DisplayName("11. Camada de Blindagem — Testes de Concorrência Reentrante")
    class ConcurrencyTests {

        @Test
        @DisplayName("CT-066 ao CT-070: Cliques Simultâneos — Duas requisições paralelas com o mesmo e-mail devem bater na barreira de estado")
        void ct066_deveImpedirDuplicidadePorCliquesSimultaneos() {
            RegistroDeliveryRequestDTO dto = new RegistroDeliveryRequestDTO("Concorrente", "paralelo@test.com", "777", "123");

            // Simula a primeira thread lendo falso (vago) e a segunda thread batendo na barreira milissegundos depois
            when(contaDeliveryRepository.existsByEmail("paralelo@test.com"))
                    .thenReturn(false)
                    .thenReturn(true);

            when(clienteRepository.findByNumero("777")).thenReturn(Optional.empty());
            when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteExistenteMock);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password"); // Mock para passwordEncoder
            when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0)); // Adicionado mock para save

            // Primeira transação passa limpa
            contaDeliveryService.registrarNovaConta(dto);

            // Segunda transação síncrona/concorrente é interceptada e bloqueada
            assertThrows(BusinessRuleException.class, () -> contaDeliveryService.registrarNovaConta(dto));
            verify(contaDeliveryRepository, times(1)).save(any(ContaDelivery.class));
        }
    }
}