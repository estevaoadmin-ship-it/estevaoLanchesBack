package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaRepository extends JpaRepository<Conta, UUID> {

    /**
     * 🎯 REAJUSTADO: Busca todas as subcontas atreladas a uma sessão de mesa.
     */
    List<Conta> findByComandaId(UUID comandaId);

    /**
     * 🎯 REAJUSTADO: Localiza uma partição específica de subconta dentro de uma comanda mestre.
     */
    Optional<Conta> findByComandaIdAndNumeroConta(UUID comandaId, Integer numeroConta);
}