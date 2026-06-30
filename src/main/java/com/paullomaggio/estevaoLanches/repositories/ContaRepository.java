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
     * Busca todas as subcontas atreladas a uma sessão de mesa.
     * Não garante ordem específica.
     */
    List<Conta> findByComandaId(UUID comandaId);

    /**
     * Busca todas as subcontas atreladas a uma sessão de mesa, ordenadas pelo número da conta.
     */
    List<Conta> findByComandaIdOrderByNumeroContaAsc(UUID comandaId);

    /**
     * Localiza uma partição específica de subconta dentro de uma comanda mestre.
     */
    Optional<Conta> findByComandaIdAndNumeroConta(UUID comandaId, Integer numeroConta);
}