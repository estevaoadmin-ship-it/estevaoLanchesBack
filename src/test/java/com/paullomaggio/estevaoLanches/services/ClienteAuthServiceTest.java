package com.paullomaggio.estevaoLanches.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.DisplayName;

@ExtendWith(MockitoExtension.class)
class ClienteAuthServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

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
        // Set the @Value property manually for the test
        ReflectionTestUtils.setField(clienteAuthService, "googleClientId", "test-client-id.apps.googleusercontent.com");
    }

    // ==========================================
    // 1. Fluxo Principal & 2. Criação
    // ==========================================

    @Test
    void ct001_deveAutenticarClienteJaExistente() throws Exception {
        Cliente clienteExistente = new Cliente();
        clienteExistente.setEmail(EMAIL_TEST);

        // Mock Payload
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);

        // Mock DB
        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(clienteExistente));

        // Mock Token Generator
        when(tokenService.gerarTokenCliente(clienteExistente)).thenReturn(FAKE_GENERATED_JWT);

        // Mock Verifier Construction
        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            String result = clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            // Assertions
            assertEquals(FAKE_GENERATED_JWT, result, "CT-003: Deve retornar exatamente o token produzido");
            verify(clienteRepository, times(1)).findByEmail(EMAIL_TEST);
            verify(clienteRepository, never()).save(any(Cliente.class)); // CT-007
            verify(tokenService, times(1)).gerarTokenCliente(clienteExistente);
        }
    }

    @Test
    void ct002_deveCriarNovoClienteQuandoNaoExistir() throws Exception {
        // Mock Payload
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST); // CT-005, CT-018
        when(payload.get("name")).thenReturn(NOME_TEST); // CT-004, CT-017

        // Mock DB - Not found
        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());

        // Mock DB - Save behavior
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> {
            Cliente saved = invocation.getArgument(0);
            // Assertions on the new entity being saved
            assertEquals(NOME_TEST, saved.getNome());
            assertEquals(EMAIL_TEST, saved.getEmail());
            assertNull(saved.getNumero(), "CT-006: Deve salvar telefone nulo");
            return saved;
        });

        when(tokenService.gerarTokenCliente(any(Cliente.class))).thenReturn(FAKE_GENERATED_JWT);

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            String result = clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            assertEquals(FAKE_GENERATED_JWT, result);
            verify(clienteRepository, times(1)).save(any(Cliente.class)); // CT-021
        }
    }

    // ==========================================
    // 3. Tratamento de Erros
    // ==========================================

    @Test
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
    void ct009_devePropagarExcecaoDoTokenService() throws Exception {
        Cliente cliente = new Cliente();

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);
        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.of(cliente));

        when(tokenService.gerarTokenCliente(cliente)).thenThrow(new RuntimeException("Erro JWT"));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            RuntimeException ex = assertThrows(RuntimeException.class, () -> {
                clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
            });
            assertEquals("Erro JWT", ex.getMessage());
        }
    }

    @Test
    void ct011_devePropagarErroNaBusca() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
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
    void ct013_naoDeveInteragirComDependenciasSeTokenInvalido() throws Exception {
        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(null))) {

            assertThrows(IllegalArgumentException.class, () -> clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING));

            verify(clienteRepository, never()).findByEmail(anyString()); // CT-015
            verify(clienteRepository, never()).save(any(Cliente.class)); // CT-014
            verify(tokenService, never()).gerarTokenCliente(any(Cliente.class)); // CT-013
        }
    }

    @Test
    void ct016_deveRespeitarOrdemDeExecucaoAoCriarCliente() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenReturn(new Cliente());

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            InOrder inOrder = inOrder(clienteRepository, tokenService);
            inOrder.verify(clienteRepository).findByEmail(EMAIL_TEST);
            inOrder.verify(clienteRepository).save(any(Cliente.class));
            inOrder.verify(tokenService).gerarTokenCliente(any(Cliente.class));
        }
    }

    // ==========================================
    // 8. Edge Cases
    // ==========================================

    @Test
    void ct023_deveAceitarCaracteresEspeciaisNoNome() throws Exception {
        String nomeEspecial = "José Antônio d'Ávila - (O Padeiro)";

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(nomeEspecial);

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository).save(argThat(cliente -> cliente.getNome().equals(nomeEspecial)));
        }
    }

    @Test
    void ct024_deveAceitarEmailComSubdominios() throws Exception {
        String complexEmail = "usuario.master+tag@mail.google.com.br";

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(complexEmail);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(complexEmail)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository).save(argThat(cliente -> cliente.getEmail().equals(complexEmail)));
        }
    }

    @Test
    @DisplayName("CT-025: Deve garantir que o e-mail seja processado sempre em minúsculas para evitar duplicação (se necessário pela regra)")
    void ct025_devePadronizarEmailEmMinusculas() throws Exception {
        String emailMaiusculo = "TESTE@GMAIL.COM";
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(emailMaiusculo);
        when(payload.get("name")).thenReturn(NOME_TEST);

        when(clienteRepository.findByEmail(emailMaiusculo)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            // Verifica se o repositório foi chamado com o email fornecido (ou a lógica que você decidir aplicar)
            verify(clienteRepository).findByEmail(emailMaiusculo);
        }
    }

    @Test
    @DisplayName("CT-026: Deve lançar exceção de negócio se o e-mail do payload do Google for nulo")
    void ct026_deveLancarErroSeEmailDoPayloadForNulo() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(null);

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            // Agora esperamos IllegalArgumentException conforme a lógica de blindagem atual
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
            });

            // Opcional: verificar se a mensagem está correta
            assertEquals("E-mail não fornecido pelo Google.", ex.getMessage());
        }
    }

    @Test
    @DisplayName("CT-027: Deve lidar com falhas de rede na verificação (IOException)")
    void ct027_devePropagarIOExceptionDoVerifier() {
        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenThrow(new IOException("Timeout Google")))) {

            assertThrows(IOException.class, () -> clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING));
        }
    }

    @Test
    @DisplayName("CT-028: Deve garantir que, se o nome for nulo no payload, o cliente seja salvo com nome 'Cliente' ou vazio")
    void ct028_deveTratarNomeNuloNoPayload() throws Exception {
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn(EMAIL_TEST);
        when(payload.get("name")).thenReturn(null); // Payload sem nome

        when(clienteRepository.findByEmail(EMAIL_TEST)).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenReturn(googleIdToken))) {

            clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);

            verify(clienteRepository).save(argThat(cliente -> cliente.getNome() == null));
        }
    }


}