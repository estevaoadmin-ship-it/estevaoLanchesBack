package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoPesquisaDTO;
import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    /**
     * 🎯 CONSULTA REESTRUTURADA: Consolida a somatória de todas as amortizações
     * utilizando a chave relacional id da Conta mestre.
     */
    @Query("SELECT COALESCE(SUM(p.valorPago), 0) FROM Pagamento p WHERE p.conta.id = :contaId")
    BigDecimal sumPagamentosPorConta(@Param("contaId") UUID contaId);

    List<Pagamento> findByContaId(UUID contaId);

    List<Pagamento> findByPedidoId(UUID pedidoId);

    @Query("""
        SELECT COALESCE(SUM(p.valorPago), 0)
        FROM Pagamento p
        WHERE p.pedido.id = :pedidoId
    """)
    BigDecimal sumPagamentosPorPedido(
            @Param("pedidoId") UUID pedidoId
    );

    boolean existsByPedidoId(UUID pedidoId);

    List<Pagamento> findByCaixaId(UUID caixaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Pagamento p
        WHERE p.id = :id
    """)
    Optional<Pagamento> findByIdForUpdate(
            @Param("id") UUID id
    );

    

    @Query("""
        SELECT new com.paullomaggio.estevaoLanches.dtos.PagamentoPesquisaDTO(
            p,
            COALESCE(p.valorPago, 0) - COALESCE(
                (SELECT COALESCE(SUM(e.valorEstornado), 0)
                 FROM EstornoPagamento e
                 WHERE e.pagamento.id = p.id),
                0
            )
        )
        FROM Pagamento p
        LEFT JOIN p.conta c
        LEFT JOIN c.comanda cm
        LEFT JOIN cm.mesa m
        WHERE
            c.pago = true
            AND m.numero = :numeroMesa
            AND p.dataHora >= :dataLimite
        ORDER BY p.dataHora DESC
        """)
    List<PagamentoPesquisaDTO> pesquisarPorMesa(
            @Param("numeroMesa") Integer numeroMesa,
            @Param("dataLimite") LocalDateTime dataLimite
    );

    @Query("""
        SELECT new com.paullomaggio.estevaoLanches.dtos.PagamentoPesquisaDTO(
            p,
            COALESCE(p.valorPago, 0) - COALESCE(
                (SELECT COALESCE(SUM(e.valorEstornado), 0)
                 FROM EstornoPagamento e
                 WHERE e.pagamento.id = p.id),
                0
            )
        )
        FROM Pagamento p
        LEFT JOIN p.pedido pe
        LEFT JOIN p.conta c
        LEFT JOIN c.pedidos cp
        WHERE
            (pe.numeroPedido = :numeroPedido OR cp.numeroPedido = :numeroPedido)
            AND p.dataHora >= :dataLimite
        ORDER BY p.dataHora DESC
        """)
    List<PagamentoPesquisaDTO> pesquisarPorPedido(
            @Param("numeroPedido") String numeroPedido,
            @Param("dataLimite") LocalDateTime dataLimite
    );

    @Query("""
        SELECT COALESCE(SUM(p.valorPago), 0)
        FROM Pagamento p
        WHERE p.dataHora BETWEEN :inicio AND :fim
        """)
    BigDecimal somarFaturamentoBrutoPorPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("""
        SELECT new com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO(
            p.formaPagamento,
            SUM(p.valorPago)
        )
        FROM Pagamento p
        WHERE p.dataHora BETWEEN :inicio AND :fim
        GROUP BY p.formaPagamento
    """)
    List<MeioPagamentoItemDTO> somarFaturamentoPorMeioPagamentoPorPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );
}