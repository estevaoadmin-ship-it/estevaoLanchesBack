package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.RegistroDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContaDeliveryServiceTest {

    @Mock private ContaDeliveryRepository contaDeliveryRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ContaDeliveryService contaDeliveryService;

    private RegistroDeliveryRequestDTO requestValida;
    private Cliente clienteExistente;
    private String senhaCriptografada;

    @BeforeEach
    void setUp() {
        requestValida = new RegistroDeliveryRequestDTO("Paulo Fernando", "paulo@gmail.com", "16995887755", "123456");
        clienteExistente = new Cliente();
        clienteExistente.setId(UUID.randomUUID());
        clienteExistente.setNome("PAULO FERNANDO");
        clienteExistente.setNumero("16995887755");
        clienteExistente.setEmail("antigo@gmail.com");
        clienteExistente.setEnderecos(new ArrayList<>());
        senhaCriptografada = "$2a$10$hashSeguro";
    }

    @Test
    @DisplayName("CT-001: Deve cadastrar tudo do zero se e-mail e telefone forem inéditos")
    void deveCriarClienteEContaQuandoAmbosForemNovos() {
        when(contaDeliveryRepository.existsByEmail("paulo@gmail.com")).thenReturn(false);
        when(clienteRepository.findByNumero("16995887755")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn(senhaCriptografada);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        contaDeliveryService.registrarNovaConta(requestValida);

        verify(clienteRepository, times(1)).save(any(Cliente.class));
        verify(contaDeliveryRepository, times(1)).save(any(ContaDelivery.class));
    }

    @Test
    @DisplayName("CT-002: Deve reaproveitar Ficha do salão e atualizar o e-mail comercial")
    void deveReaproveitarClienteExistenteEApenasCriarConta() {
        when(contaDeliveryRepository.existsByEmail("paulo@gmail.com")).thenReturn(false);
        when(clienteRepository.findByNumero("16995887755")).thenReturn(Optional.of(clienteExistente));
        when(passwordEncoder.encode("123456")).thenReturn(senhaCriptografada);

        contaDeliveryService.registrarNovaConta(requestValida);

        assertThat(clienteExistente.getEmail()).isEqualTo("paulo@gmail.com"); // 🎯 FIX B
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("CT-003: Deve barrar criação se o e-mail digital já existir")
    void deveBarrearSeEmailJaExistirNaConta() {
        when(contaDeliveryRepository.existsByEmail("paulo@gmail.com")).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> contaDeliveryService.registrarNovaConta(requestValida));
    }

    @Test
    @DisplayName("CT-007: Deve normalizar strings de e-mail sujas vindas do payload antes de invocar o Mock")
    void deveNormalizarEmailParaMinusculoESemEspacos() {
        RegistroDeliveryRequestDTO requestSujo = new RegistroDeliveryRequestDTO("Paulo", "  PAULO@GMAIL.COM ", "16995887755", "123456");
        when(contaDeliveryRepository.existsByEmail("paulo@gmail.com")).thenReturn(false); // 🎯 FIX A
        when(clienteRepository.findByNumero("16995887755")).thenReturn(Optional.of(clienteExistente));
        when(passwordEncoder.encode("123456")).thenReturn(senhaCriptografada);

        contaDeliveryService.registrarNovaConta(requestSujo);
        verify(contaDeliveryRepository).existsByEmail("paulo@gmail.com");
    }
}