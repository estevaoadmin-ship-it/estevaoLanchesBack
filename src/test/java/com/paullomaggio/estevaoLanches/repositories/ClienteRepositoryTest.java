package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ClienteRepositoryTest {

    @Autowired
    private ClienteRepository clienteRepository;

    private Cliente clientePadrao;

    @BeforeEach
    void setUp() {
        clientePadrao = new Cliente();
        clientePadrao.setNome("João Silva");
        clientePadrao.setCpf("12345678901");
        clientePadrao.setEmail("joao@email.com");
        clientePadrao.setNumero("11999999999");
        clientePadrao.setDataNascimento(LocalDate.of(1990, 1, 1));
    }

    // --- Testes 1 e 2: Busca por CPF ---
    @Test
    @DisplayName("Teste 1: Deve encontrar cliente por CPF existente")
    void deveEncontrarClientePorCpfExistente() {
        clienteRepository.save(clientePadrao);
        Optional<Cliente> resultado = clienteRepository.findByCpf("12345678901");
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNome()).isEqualTo("João Silva");
    }

    @Test
    @DisplayName("Teste 2: Não deve encontrar cliente por CPF inexistente")
    void naoDeveEncontrarClientePorCpfInexistente() {
        Optional<Cliente> resultado = clienteRepository.findByCpf("00000000000");
        assertThat(resultado).isEmpty();
    }

    // --- Testes 3 e 4: Busca por E-mail ---
    @Test
    @DisplayName("Teste 3: Deve encontrar cliente por e-mail existente")
    void deveEncontrarClientePorEmailExistente() {
        clienteRepository.save(clientePadrao);
        Optional<Cliente> resultado = clienteRepository.findByEmail("joao@email.com");
        assertThat(resultado).isPresent();
    }

    @Test
    @DisplayName("Teste 4: Não deve encontrar cliente por e-mail inexistente")
    void naoDeveEncontrarClientePorEmailInexistente() {
        Optional<Cliente> resultado = clienteRepository.findByEmail("inexistente@email.com");
        assertThat(resultado).isEmpty();
    }

    // --- Testes 5 e 6: Busca por Nome ---
    @Test
    @DisplayName("Teste 5: Deve buscar clientes pelo nome ignorando maiúsculas e minúsculas")
    void deveBuscarClientesPeloNomeIgnorandoCase() {
        clienteRepository.save(clientePadrao);
        List<Cliente> resultado = clienteRepository.findByNomeContainingIgnoreCase("joão");
        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("Teste 6: Deve retornar lista vazia quando nome não existir")
    void deveRetornarListaVaziaQuandoNomeNaoExistir() {
        List<Cliente> resultado = clienteRepository.findByNomeContainingIgnoreCase("Maria");
        assertThat(resultado).isEmpty();
    }

    // --- Testes 7 a 10: Validações de Duplicidade (ExistsBy) ---
    @Test
    @DisplayName("Teste 7: Deve retornar true para CPF existente em outro cliente")
    void deveRetornarTrueParaCpfExistenteEmOutroCliente() {
        clienteRepository.save(clientePadrao);
        boolean existe = clienteRepository.existsByCpfAndIdNot("12345678901", UUID.randomUUID());
        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("Teste 8: Deve retornar false para CPF inexistente em outro cliente")
    void deveRetornarFalseParaCpfInexistenteEmOutroCliente() {
        Cliente clienteSalvo = clienteRepository.save(clientePadrao);
        boolean existe = clienteRepository.existsByCpfAndIdNot("00000000000", clienteSalvo.getId());
        assertThat(existe).isFalse();
    }

    @Test
    @DisplayName("Teste 9: Deve retornar true para e-mail existente em outro cliente")
    void deveRetornarTrueParaEmailExistenteEmOutroCliente() {
        clienteRepository.save(clientePadrao);
        boolean existe = clienteRepository.existsByEmailAndIdNot("joao@email.com", UUID.randomUUID());
        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("Teste 10: Deve retornar false para e-mail inexistente em outro cliente")
    void deveRetornarFalseParaEmailInexistenteEmOutroCliente() {
        Cliente clienteSalvo = clienteRepository.save(clientePadrao);
        boolean existe = clienteRepository.existsByEmailAndIdNot("novo@email.com", clienteSalvo.getId());
        assertThat(existe).isFalse();
    }

    // =========================================================================
    // 🆕 NOVOS TESTES: VALIDAÇÃO DO PRODUTO DERIVADO WHATSAPP (MÓVEL REAL)
    // =========================================================================

    @Test
    @DisplayName("Teste Mobile 1: Deve encontrar com precisão o cliente pelo número limpo de WhatsApp")
    void deveBuscarClientePorNumeroDeWhatsAppCadastrado() {
        clienteRepository.save(clientePadrao);
        Optional<Cliente> resultado = clienteRepository.findByNumero("11999999999");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNome()).isEqualTo("João Silva");
    }

    @Test
    @DisplayName("Teste Mobile 2: Deve retornar Optional vazio caso o número do lead não exista no PostgreSQL")
    void deveRetornarVazioCasoWhatsAppNaoExista() {
        Optional<Cliente> resultado = clienteRepository.findByNumero("16999995555");
        assertThat(resultado).isEmpty();
    }
}