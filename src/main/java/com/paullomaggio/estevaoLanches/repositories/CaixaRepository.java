package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaixaRepository extends JpaRepository<Caixa, UUID> {

    // Checagem rápida de existência
    boolean existsByStatus(StatusCaixa status);

    // 🚀 OTIMIZAÇÃO: Busca o único caixa aberto diretamente via índice do banco de dados
    Optional<Caixa> findByStatus(StatusCaixa status);
}