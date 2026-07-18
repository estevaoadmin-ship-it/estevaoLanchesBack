package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.entities.EstornoPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EstornoPagamentoRepository
        extends JpaRepository<EstornoPagamento, UUID> {

    List<EstornoPagamento> findByPagamento_IdOrderByDataHoraDesc(
            UUID pagamentoId
    );

    @Query("""
        SELECT COALESCE(SUM(e.valorEstornado), 0)
        FROM EstornoPagamento e
        WHERE e.pagamento.id = :pagamentoId
    """)
    BigDecimal somarValorEstornadoPorPagamentoId(
            @Param("pagamentoId") UUID pagamentoId
    );

    boolean existsByPagamento_Id(UUID pagamentoId);

    @Query("""
        SELECT COALESCE(SUM(e.valorEstornado), 0)
        FROM EstornoPagamento e
        WHERE e.pagamento.conta.id = :contaId
    """)
    BigDecimal somarValorEstornadoPorContaId(
            @Param("contaId") UUID contaId
    );

    List<EstornoPagamento> findByCaixaId(UUID caixaId);

    @Query("""
        SELECT COALESCE(SUM(e.valorEstornado), 0)
        FROM EstornoPagamento e
        WHERE e.dataHora BETWEEN :inicio AND :fim
    """)
    BigDecimal somarTotalEstornosPorPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("""
        SELECT new com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO(
            e.pagamento.formaPagamento,
            SUM(e.valorEstornado)
        )
        FROM EstornoPagamento e
        WHERE e.dataHora BETWEEN :inicio AND :fim
        GROUP BY e.pagamento.formaPagamento
    """)
    List<MeioPagamentoItemDTO> somarEstornosPorMeioPagamentoPorPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );
}