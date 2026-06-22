package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Mesa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MesaRepositoryTest {

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Deve localizar o registro físico da Mesa correspondente ao número digitado")
    void deveBuscarMesaPorNumero() {
        Mesa mesa = new Mesa();
        mesa.setNumero(35);
        entityManager.persist(mesa);
        entityManager.flush();

        Optional<Mesa> resultado = mesaRepository.findByNumero(35);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNumero()).isEqualTo(35);
    }

    @Test
    @DisplayName("Deve retornar Optional vazio se o garçom digitar um número de mesa não mapeado")
    void deveRetornarVazioParaNumeroInexistente() {
        Optional<Mesa> resultado = mesaRepository.findByNumero(99);
        assertThat(resultado).isEmpty();
    }
}