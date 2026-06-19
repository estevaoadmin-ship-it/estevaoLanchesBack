package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Subconta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubcontaRepository extends JpaRepository<Subconta, UUID> {
    // Busca uma subconta específica dentro de uma comanda
    Optional<Subconta> findByComandaIdAndNumeroConta(UUID comandaId, Integer numeroConta);
}