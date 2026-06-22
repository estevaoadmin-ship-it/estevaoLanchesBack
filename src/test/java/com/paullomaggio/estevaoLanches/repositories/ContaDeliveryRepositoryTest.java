package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ContaDeliveryRepositoryTest {

    @Autowired
    private ContaDeliveryRepository contaDeliveryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cliente clienteComercial;
    private ContaDelivery contaDigital;

    @BeforeEach
    void setUp() {
        // Cria e persiste a base comercial obrigatória (Cliente sem e-mail para simular fluxo real do salão)
        clienteComercial = new Cliente();
        clienteComercial.setNome("PAULO FERNANDO");
        clienteComercial.setNumero("16995887755");
        entityManager.persist(clienteComercial);

        // Instancia a credencial digital correspondente
        contaDigital = new ContaDelivery();
        contaDigital.setEmail("paulo.delivery@gmail.com");
        contaDigital.setSenha("$2a$10$hashSeguroBCrypt");
        contaDigital.setAtivo(true);
        contaDigital.setRole("ROLE_CLIENTE");
        contaDigital.setCliente(clienteComercial);
    }

    @Test
    @DisplayName("Deve localizar com precisão uma ContaDelivery ativa através do e-mail de login")
    void deveBuscarContaPorEmail() {
        contaDeliveryRepository.save(contaDigital);

        Optional<ContaDelivery> resultado = contaDeliveryRepository.findByEmail("paulo.delivery@gmail.com");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getCliente().getNome()).isEqualTo("PAULO FERNANDO");
        assertThat(resultado.get().getSenha()).startsWith("$2a$");
    }

    @Test
    @DisplayName("Deve retornar Optional vazio caso o e-mail pesquisado não conste na base digital")
    void deveRetornarVazioParaEmailInexistente() {
        Optional<ContaDelivery> resultado = contaDeliveryRepository.findByEmail("inexistente@gmail.com");
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar true se o e-mail informado já estiver ocupado por outra ContaDelivery")
    void deveRetornarTrueSeEmailJaExistir() {
        contaDeliveryRepository.save(contaDigital);

        boolean existe = contaDeliveryRepository.existsByEmail("paulo.delivery@gmail.com");
        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false se o e-mail estiver totalmente livre para novos cadastros no app")
    void deveRetornarFalseSeEmailNaoExistir() {
        boolean existe = contaDeliveryRepository.existsByEmail("livre@gmail.com");
        assertThat(existe).isFalse();
    }
}