package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    /**
     * Soma todos os pagamentos já registrados para uma conta filha específica
     * de um determinado pedido.
     */
    @Query("SELECT COALESCE(SUM(p.valorPago), 0) FROM Pagamento p " +
            "WHERE p.pedidoId = :pedidoId AND p.numeroConta = :numeroConta")
    BigDecimal sumPagamentosPorConta(
            @Param("pedidoId") UUID pedidoId,
            @Param("numeroConta") Integer numeroConta
    );
}