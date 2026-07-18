package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.entities.Pagamento;
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