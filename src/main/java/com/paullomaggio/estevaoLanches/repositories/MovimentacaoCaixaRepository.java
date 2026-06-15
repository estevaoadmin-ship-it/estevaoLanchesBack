package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.MovimentacaoCaixa;
import com.paullomaggio.estevaoLanches.enums.TipoMovimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixa, UUID> {

    List<MovimentacaoCaixa> findByCaixaIdOrderByDataHoraDesc(UUID caixaId);

    @Query("SELECT COALESCE(SUM(m.valor), 0) FROM MovimentacaoCaixa m " +
            "WHERE m.caixa.id = :caixaId AND m.tipo = :tipo AND m.cancelada = false")
    BigDecimal somarPorCaixaETipo(@Param("caixaId") UUID caixaId, @Param("tipo") TipoMovimentacao tipo);
}