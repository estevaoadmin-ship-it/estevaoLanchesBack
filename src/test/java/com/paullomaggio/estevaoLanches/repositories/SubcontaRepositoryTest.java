package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.entities.Subconta;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SubcontaRepositoryTest {

    @Autowired
    private SubcontaRepository subcontaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("🎯 FIX MESA_ID: Monta o grafo relacional completo e válido salvando a Mesa antes da Comanda")
    void deveBuscarSubcontaPorComandaENumero() {
        // 1. 🎯 FIX: Criação e persistência da Mesa obrigatória para satisfazer a FK da Comanda
        Mesa mesaSalão = new Mesa();
        mesaSalão.setNumero(25);
        mesaSalão.setStatus(StatusMesa.LIVRE);
        mesaSalão.setEmpresaId(UUID.randomUUID());
        mesaSalão.setFilialId(UUID.randomUUID());
        entityManager.persist(mesaSalão);

        // 2. Criação da Comanda vinculando os dados corporativos e a mesa gerada acima
        Comanda comandaPai = new Comanda();
        comandaPai.setEmpresaId(UUID.randomUUID());
        comandaPai.setFilialId(UUID.randomUUID());
        comandaPai.setStatus(StatusComanda.ABERTA);
        comandaPai.setMesa(mesaSalão); // 🎯 FIX: Atribuída a dependência física para mitigar o erro de NOT NULL

        entityManager.persist(comandaPai);

        // 3. Criação e persistência da subconta fracionada
        Subconta sub = new Subconta();
        sub.setComanda(comandaPai);
        sub.setNumeroConta(3);

        entityManager.persist(sub);
        entityManager.flush();

        Optional<Subconta> resultado = subcontaRepository.findByComandaIdAndNumeroConta(comandaPai.getId(), 3);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNumeroConta()).isEqualTo(3);
    }
}