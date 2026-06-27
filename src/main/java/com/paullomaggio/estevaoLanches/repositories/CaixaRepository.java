package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaixaRepository extends JpaRepository<Caixa, UUID> {

    /**
     * Verifica a existência de registros correspondentes ao status informado.
     */
    boolean existsByStatus(StatusCaixa status);

    /**
     * Busca um registro de caixa com base em seu status de funcionamento atual.
     */
    Optional<Caixa> findByStatus(StatusCaixa status);
}