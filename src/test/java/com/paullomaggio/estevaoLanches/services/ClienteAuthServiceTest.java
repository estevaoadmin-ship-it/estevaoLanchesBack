package com.paullomaggio.estevaoLanches.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.paullomaggio.estevaoLanches.controllers.AutenticacaoController;
import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Autenticação e Segurança — Matriz de Blindagem Multicanal")
class ClienteAuthServiceTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private ContaDeliveryRepository contaDeliveryRepository;
    @Mock private TokenService tokenService;
    @Mock private GoogleIdToken googleIdToken;
    @Mock private GoogleIdToken.Payload payload;

    @InjectMocks private ClienteAuthService clienteAuthService;

    private static final String FAKE_TOKEN_STRING = "fake-jwt-token-string";
    private static final String FAKE_GENERATED_JWT = "abc123.jwt.token";
    private static final String EMAIL_TEST = "teste@gmail.com";
    private static final String NOME_TEST = "Paulo Maggio";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clienteAuthService, "googleClientId", "test-client-id.apps.googleusercontent.com");
    }

    // =========================================================================
    // BLOCO A & C — FLUXO PRINCIPAL GOOGLE AUTH & CLIENTE COMERCIAL
    // =========================================================================
    @Nested
    @DisplayName("Bloco A & C — Fluxos de Autenticação Google e Identidade Comercial")
    class GoogleAuthHappyPathTests {

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
    }

    // =========================================================================
    // BLOCO B — GOOGLE TOKEN SECURITY & EXCEPTIONS
    // =========================================================================
    @Nested
    @DisplayName("Bloco B — Segurança e Exceções do Token do Google")
    class GoogleTokenSecurityTests {

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
        @DisplayName("CT-012: Deve propagar falhas de criptografia e chaves do Verifier oficial do Google")
        void ct012_devePropagarExcecaoDoVerifier() {
            try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                    (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenThrow(new GeneralSecurityException("Invalid key")))) {

                assertThrows(GeneralSecurityException.class, () -> {
                    clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING);
                });
            }
        }

        @Test
        @DisplayName("CT-027: Deve propagar exceções de timeout de rede e E/S na verificação com a API do Google")
        void ct027_devePropagarIOExceptionDoVerifier() {
            try (MockedConstruction<GoogleIdTokenVerifier> mocked = mockConstruction(GoogleIdTokenVerifier.class,
                    (mock, context) -> when(mock.verify(FAKE_TOKEN_STRING)).thenThrow(new IOException("Timeout Google")))) {

                assertThrows(IOException.class, () -> clienteAuthService.autenticarComGoogle(FAKE_TOKEN_STRING));
            }
        }
    }

    // =========================================================================
    // BLOCO D — CONTA DELIVERY PROPROPERTIES VALIDATION
    // =========================================================================
    @Nested
    @DisplayName("Bloco D — Regras Estritas de Criação da ContaDelivery")
    class ContaDeliveryValidationTests {

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
                assertNotNull(contaGerada.getCliente());
            }
        }
    }

    // =========================================================================
    // BLOCO F — ORDEM TRANSACIONAL DE PERSISTÊNCIA
    // =========================================================================
    @Nested
    @DisplayName("Bloco F — Auditoria de Ordem Transacional Estrita")
    class TransactionalOrderingTests {

        @Test
        @DisplayName("CT-016: Deve seguir a ordem rígida de persistência comercial e digital (Cliente -> ContaDelivery -> Token)")
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
    }

    // =========================================================================
    // BLOCO I — CONVERSÃO MESA -> DELIVERY (REAPROVEITAMENTO OPERACIONAL)
    // =========================================================================
    @Nested
    @DisplayName("Bloco I — Transição Multicanal (Mesa para Delivery)")
    class MultiChannelTransitionTests {

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
                verify(clienteRepository, never()).save(any(Cliente.class)); // 🎯 Proteção contra duplicação comercial
                verify(contaDeliveryRepository, times(1)).save(any(ContaDelivery.class));
            }
        }
    }

    // =========================================================================
    // BLOCO J — HIGIENIZAÇÃO DE INPUTS, UNICODE E SEGURANÇA (SANITY)
    // =========================================================================
    @Nested
    @DisplayName("Bloco J — Sanitização de Dados e Blindagem contra Injeções")
    class InputSanitizationAndSecurityTests {

        @Test
        @DisplayName("CT-023: Deve aceitar codificação e caracteres Unicode complexos no Nome oriundo do Google")
        void ct023_deveAceitarCaracteresEspeciaisNoNome() throws Exception {
            String nomeEspecial = "José Antônio d'Ávila - 👑 (王)";

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
        @DisplayName("CT-024: Deve processar corretamente e-mails longos contendo subdomínios e tags complexas (Ex: +tag)")
        void ct024_deveAceitarEmailComSubdominios() throws Exception {
            String complexEmail = "usuario.master+fidelidade@mail.google.com.br";

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
    }

    // =========================================================================
    // BLOCO N — SPRING SECURITY DETAILS CONTRACT VALIDATION
    // =========================================================================
    @Nested
    @DisplayName("Bloco N — Verificação do Contrato Estrito do Spring Security (UserDetails)")
    class SpringSecurityUserDetailsTests {

        @Test
        @DisplayName("Deve verificar se as diretivas de roles do Spring Security concatenam perfeitamente com o prefixo 'ROLE_'")
        void deveGarantirPrefixoRoleCorreto() {
            Usuario usuario = new Usuario(UUID.randomUUID(), "Atendente", "atend@estevao.com", "hash", "GARCOM", true);

            var authorities = usuario.getAuthorities();

            // 🎯 FIX DEFINITIVO: Troca do assertThat do AssertJ pelo assertEquals nativo do JUnit 5
            assertEquals(1, authorities.size());
            assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_GARCOM")));
            assertEquals("hash", usuario.getPassword());
            assertEquals("atend@estevao.com", usuario.getUsername());
            assertTrue(usuario.isAccountNonExpired());
            assertTrue(usuario.isAccountNonLocked());
            assertTrue(usuario.isCredentialsNonExpired());
            assertTrue(usuario.isEnabled());
        }

    // =========================================================================
    // BLOCO O, P & Q — CAMADA DE CONTROLLER & AUDITORIA OPERACIONAL
    // =========================================================================
    @Nested
    @DisplayName("Bloco O, P & Q — Testes do Controlador de Autenticação (PDV e App)")
    class AutenticacaoControllerUnitTests {

        private AuthenticationManager authManager;
        private TokenService tokService;
        private ContaDeliveryService deliveryService;
        private PasswordEncoder encoder;
        private AutenticacaoController controller;

        @BeforeEach
        void setUpController() {
            authManager = mock(AuthenticationManager.class);
            tokService = mock(TokenService.class);
            deliveryService = mock(ContaDeliveryService.class);
            encoder = mock(PasswordEncoder.class);
            controller = new AutenticacaoController(authManager, tokService, deliveryService, contaDeliveryRepository, encoder);
        }

        @Test
        @DisplayName("Login PDV Central (200 OK) — Funcionário autenticado deve gerar token e devolver dados limpos")
        void loginPdvSucesso() {
            LoginRequestDTO dto = new LoginRequestDTO("admin@estevao.com", "123456");
            Usuario usuarioFake = new Usuario(UUID.randomUUID(), "Admin", "admin@estevao.com", "hash", "ADMIN", true);
            Authentication authResult = mock(Authentication.class);

            when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authResult);
            when(authResult.getPrincipal()).thenReturn(usuarioFake);
            when(tokService.gerarToken(usuarioFake)).thenReturn("jwt-pdv");

            ResponseEntity<?> response = controller.login(dto);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("Login PDV Central (401 Unauthorized) — Credencial incorreta deve retornar barreira atômica limpa")
        void loginPdvFalha() {
            LoginRequestDTO dto = new LoginRequestDTO("admin@estevao.com", "senha-errada");
            when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalido"));

            ResponseEntity<?> response = controller.login(dto);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNull(response.getBody());
        }

        @Test
        @DisplayName("Login App Delivery (401 Unauthorized) — Validação de hash nativo incorreto para clientes")
        void loginClienteSenhaIncorreta() {
            LoginRequestDTO dto = new LoginRequestDTO("cliente@delivery.com", "errada");
            ContaDelivery conta = new ContaDelivery();
            conta.setSenha("hash-correto");

            when(contaDeliveryRepository.findByEmail("cliente@delivery.com")).thenReturn(Optional.of(conta));
            when(encoder.matches("errada", "hash-correto")).thenReturn(false);

            ResponseEntity<?> response = controller.loginCliente(dto);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("Login App Delivery (403 Forbidden) — Deve reter logins em contas marcadas como inativas")
        void loginClienteContaInativa() {
            LoginRequestDTO dto = new LoginRequestDTO("inativo@delivery.com", "123");
            ContaDelivery conta = new ContaDelivery();
            conta.setSenha("hash");
            conta.setAtivo(false); // 🎯 Inativa no salão

            when(contaDeliveryRepository.findByEmail("inativo@delivery.com")).thenReturn(Optional.of(conta));
            when(encoder.matches("123", "hash")).thenReturn(true);

            ResponseEntity<?> response = controller.loginCliente(dto);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }
    }}
}