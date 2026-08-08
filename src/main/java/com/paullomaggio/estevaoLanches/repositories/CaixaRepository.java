package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    // =========================================================================
    // CONSULTAS DE HISTÓRICO DE CAIXA (consulta por período, mais recente primeiro)
    // =========================================================================

    /**
     * Retorna todos os turnos de caixa, do mais recente para o mais antigo.
     */
    List<Caixa> findAllByOrderByDataHoraAberturaDesc();

    /**
     * Retorna turnos de caixa a partir de uma data inicial (inclusive), do mais
     * recente para o mais antigo.
     */
    List<Caixa> findByDataHoraAberturaGreaterThanEqualOrderByDataHoraAberturaDesc(LocalDateTime dataInicial);

    /**
     * Retorna turnos de caixa até uma data final (inclusive), do mais recente para
     * o mais antigo.
     */
    List<Caixa> findByDataHoraAberturaLessThanEqualOrderByDataHoraAberturaDesc(LocalDateTime dataFinal);

    /**
     * Retorna turnos de caixa dentro de um intervalo [dataInicial, dataFinal],
     * do mais recente para o mais antigo.
     */
    List<Caixa> findByDataHoraAberturaBetweenOrderByDataHoraAberturaDesc(
            LocalDateTime dataInicial, LocalDateTime dataFinal);

    /**
     * Retorna turnos de caixa FECHADOS, do mais recente para o mais antigo.
     */
    List<Caixa> findByStatusOrderByDataHoraAberturaDesc(StatusCaixa status);
}
