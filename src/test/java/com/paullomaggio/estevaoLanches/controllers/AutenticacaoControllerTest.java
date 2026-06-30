package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.LoginRequestDTO;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import com.paullomaggio.estevaoLanches.services.ContaDeliveryService;
import com.paullomaggio.estevaoLanches.services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Bloco O, P & Q — Testes do Controlador de Autenticação (PDV e App)")
class AutenticacaoControllerTest {

    @Mock private AuthenticationManager authManager;
    @Mock private TokenService tokService;
    @Mock private ContaDeliveryService deliveryService;
    @Mock private ContaDeliveryRepository contaDeliveryRepository;
    @Mock private PasswordEncoder encoder;

    private AutenticacaoController controller;

    @BeforeEach
    void setUp() {
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
}