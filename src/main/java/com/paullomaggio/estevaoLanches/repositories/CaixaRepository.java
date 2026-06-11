package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CaixaRepository extends JpaRepository<Caixa, UUID> {
    // Retorna true se houver qualquer registro com status ABERTO no banco
    boolean existsByStatus(StatusCaixa status);
}