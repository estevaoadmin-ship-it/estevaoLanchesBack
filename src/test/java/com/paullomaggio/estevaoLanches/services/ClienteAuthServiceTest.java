package com.paullomaggio.estevaoLanches.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor; // 🎯 FIX: Importação adicionada para resolver o símbolo oculto
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteAuthServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ContaDeliveryRepository contaDeliveryRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private GoogleIdToken googleIdToken;

    @Mock
    private GoogleIdToken.Payload payload;

    @InjectMocks
    private ClienteAuthService clienteAuthService;

    private static final String FAKE_TOKEN_STRING = "fake-jwt-token-string";
    private static final String FAKE_GENERATED_JWT = "abc123.jwt.token";
    private static final String EMAIL_TEST = "teste@gmail.com";
    private static final String NOME_TEST = "Paulo Maggio";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clienteAuthService, "googleClientId", "test-client-id.apps.googleusercontent.com");
    }

    // ==========================================
    // 1. Fluxo Principal & 2. Criação
    // ==========================================

    @Test
    @DisplayName("CT-001: Deve autenticar com sucesso cliente e conta digital já existentes no banco")
    void ct001_deveAutenticarClienteJaExistente() throws Exception {
        Cliente clienteExistente = new Cliente();
        clienteExistente.setEmail(EMAIL_TEST);

        ContaDelivery contaExistente = new ContaDelivery();
        contaExistente.setEmail(EMAIL_TEST);
        contaExistente.setCliente(clienteExistente);

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(clienteExistente));
        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(contaExistente));
        when(tokenService.gerarTokenCliente(contaExistente)).thenReturn(FAKE_GENERATED_JWT);

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            String result = clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            assertEquals(FAKE_GENERATED_JWT, result);
            verify(clienteRepository, times(1)).findByEmail(EMAIL_TEST);
            verify(clienteRepository, never()).save(any(Cliente.class));
            verify(contaDeliveryRepository, times(1)).findByEmail(EMAIL_TEST);
            verify(contaDeliveryRepository, never()).save(any(ContaDelivery.class));
            verify(tokenService, times(1)).gerarTokenCliente(contaExistente);
        }
    }

    @Test
    @DisplayName("CT-002: Deve criar novos registros (Cliente + ContaDelivery) de forma limpa quando não existirem")
    void ct002_deveCriarNovoClienteQuandoNaoExistir() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
            Cliente saved = invocation.getArgument(0);
            assertEquals(NOME_TEST, saved.getNome());
            assertEquals(EMAIL_TEST, saved.getEmail());
            assertNull(saved.getNumero());
            return saved;
        });

        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(tokenService.gerarTokenCliente(any(ContaDelivery.class))).thenReturn(FAKE_GENERATED_JWT);

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            String result = clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            assertEquals(FAKE_GENERATED_JWT, result);
            verify(clienteRepository, times(1)).save(any(Cliente.class));
            verify(contaDeliveryRepository, times(1)).save(any(ContaDelivery.class));
        }
    }

    // ==========================================
    // 3. Tratamento de Erros
    // ==========================================

    @Test
    @DisplayName("CT-008: Deve barrar autenticação se o validador do Google invalidar a assinatura do token")
    void ct008_deveLancarExceptionQuandoTokenInvalido() throws Exception {
        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(null))) {

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
            });

            assertEquals("Token do Google inválido ou expirado.", ex.getMessage());
        }
    }

    @Test
    @DisplayName("CT-009: Deve propagar falhas internas vindas do gerador de Tokens JWT")
    void ct009_devePropagarExcecaoDoTokenService() throws Exception {
        Cliente cliente = new Cliente();
        ContaDelivery conta = new ContaDelivery();

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);
        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(cliente));
        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(conta));

        when(tokenService.gerarTokenCliente(conta)).thenThrow(new RuntimeException("Erro JWT"));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            RuntimeException ex = assertThrows(RuntimeException.class, () -> {
                clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
            });
            assertEquals("Erro JWT", ex.getMessage());
        }
    }

    @Test
    @DisplayName("CT-011: Deve propagar erros de infraestrutura de leitura/conexão com o PostgreSQL")
    void ct011_devePropagarErroNaBusca() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenThrow(new DataRetrievalFailureException("DB Down"));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            assertThrows(DataRetrievalFailureException.class, () -> {
                clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
            });
        }
    }

    @Test
    @DisplayName("CT-012: Deve propagar falhas de criptografia e chaves do Verifier oficial do Google")
    void ct012_devePropagarExcecaoDoVerifier() {
        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenThrow(new GeneralSecurityException("Invalid key")))) {

            assertThrows(GeneralSecurityException.class, () -> {
                clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
            });
        }
    }

    // ==========================================
    // 4. Casos de Segurança & Interação
    // ==========================================

    @Test
    @DisplayName("CT-013: Não deve tocar em nenhum repositório comercial ou digital se o token original for nulo")
    void ct013_naoDeveInteragirComDependenciasSeTokenInvalido() throws Exception {
        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(null))) {

            assertThrows(IllegalArgumentException.class, () -> clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING));

            verify(clienteRepository, never()).findByEmail(anyString());
            verify(clienteRepository, never()).save(any(Cliente.class));
            verify(contaDeliveryRepository, never()).findByEmail(anyString());
            verify(contaDeliveryRepository, never()).save(any(ContaDelivery.class));
            verify(tokenService, never()).gerarTokenCliente(any(ContaDelivery.class));
        }
    }

    @Test
    @DisplayName("CT-016: Deve seguir a ordem rígida de persistência transacional (Cliente -> ContaDelivery -> Token)")
    void ct016_deveRespeitarOrdemDeExecucaoAoCriarCliente() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenReturn(new Cliente());

        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenReturn(new ContaDelivery());

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            InOrder inOrder = inOrder(clienteRepository, contaDeliveryRepository, tokenService);
            inOrder.verify(clienteRepository).findByEmail(EMAIL_TEST);
            inOrder.verify(clienteRepository).save(any(Cliente.class));
            inOrder.verify(contaDeliveryRepository).findByEmail(EMAIL_TEST);
            inOrder.verify(contaDeliveryRepository).save(any(ContaDelivery.class));
            inOrder.verify(tokenService).gerarTokenCliente(any(ContaDelivery.class));
        }
    }

    // ==========================================
    // 8. Edge Cases
    // ==========================================

    @Test
    @DisplayName("CT-023: Deve aceitar codificação e caracteres especiais complexos no Nome do Google")
    void ct023_deveAceitarCaracteresEspeciaisNoNome() throws Exception {
        String nomeEspecial = "José Antônio d'Ávila - (O Padeiro)";

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(nomeEspecial);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository).save(argThat(cliente -> cliente.getNome().equals(nomeEspecial)));
        }
    }

    @Test
    @DisplayName("CT-024: Deve processar corretamente e-mails longos contendo subdomínios e tags complexas")
    void ct024_deveAceitarEmailComSubdominios() throws Exception {
        String complexEmail = "usuario.master+tag@mail.google.com.br";

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(complexEmail);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(complexEmail)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        when(contaDeliveryRepository.findByEmail(complexEmail)).thenReturn(Optional.empty());
        when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository).save(argThat(cliente -> cliente.getEmail().equals(complexEmail)));
        }
    }

    @Test
    @DisplayName("CT-025: Deve garantir que a busca comercial seja acionada com o e-mail em caixa alta se fornecido assim")
    void ct025_devePadronizarEmailEmMinusculas() throws Exception {
        String emailMaiusculo = "TESTE@GMAIL.COM";
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(emailMaiusculo);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(emailMaiusculo)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        when(contaDeliveryRepository.findByEmail(emailMaiusculo)).thenReturn(Optional.empty());
        when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository).findByEmail(emailMaiusculo);
        }
    }

    @Test
    @DisplayName("CT-026: Deve lançar erro de negócio se o payload do Google vier corrompido com e-mail nulo")
    void ct026_deveLancarErroSeEmailDoPayloadForNulo() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(null);

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
            });

            assertEquals("E-mail não fornecido pelo Google.", ex.getMessage());
        }
    }

    @Test
    @DisplayName("CT-027: Deve propagar exceções de timeout de rede e E/S na verificação")
    void ct027_devePropagarIOExceptionDoVerifier() {
        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenThrow(new IOException("Timeout Google")))) {

            assertThrows(IOException.class, () -> clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING));
        }
    }

    @Test
    @DisplayName("CT-028: Deve aceitar o processamento seguro mesmo se o nome vier ausente/nulo no Google")
    void ct028_deveTratarNomeNuloNoPayload() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(null);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository).save(argThat(cliente -> cliente.getNome() == null));
        }
    }

    // =========================================================================
    // 🆕 9. NOVOS TESTES EXCLUSIVOS PARA A MÁQUINA DE ATENDIMENTO EVOLUTIVO
    // =========================================================================

    @Test
    @DisplayName("CT-029: Deve reaproveitar Ficha Comercial existente do Salão mas criar nova ContaDelivery de segurança")
    void ct029_deveReaproveitarClienteExistenteMasCriarNovaContaDelivery() throws Exception {
        Cliente clienteSalaoExistente = new Cliente();
        clienteSalaoExistente.setId(UUID.randomUUID());
        clienteSalaoExistente.setNome("Paulo Mesa");
        clienteSalaoExistente.setEmail(EMAIL_TEST);

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn("Paulo Mesa");

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(clienteSalaoExistente));
        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(contaDeliveryRepository.save(any(ContaDelivery.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository, times(1)).findByEmail(EMAIL_TEST);
            verify(clienteRepository, never()).save(any(Cliente.class));
            verify(contaDeliveryRepository, times(1)).save(any(ContaDelivery.class));
        }
    }

    @Test
    @DisplayName("CT-030: Deve validar o contrato de mapeamento padrão e isolado ao gerar uma nova ContaDelivery")
    void ct030_deveGarantirMapeamentoCorretoAoCriarNovaContaDelivery() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(contaDeliveryRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());

        ArgumentCaptor<ContaDelivery> contaCaptor = ArgumentCaptor.forClass(ContaDelivery.class);
        when(contaDeliveryRepository.save(contaCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            ContaDelivery contaGerada = contaCaptor.getValue();
            assertNotNull(contaGerada);
            assertEquals(EMAIL_TEST, contaGerada.getEmail());
            assertEquals("", contaGerada.getSenha());
            assertTrue(contaGerada.isAtivo());
            assertEquals("ROLE_CLIENTE", contaGerada.getRole());
        }
    }
}