package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    List<Pedido> findByClienteIdOrderByDataHoraDesc(UUID clienteId);

    Optional<Pedido> findByNumeroPedido(String numeroPedido);

    // 🚀 NOVO MONITOR DA COZINHA: Só lista o pedido se pelo menos UM item dele precisar de preparo
    @Query("SELECT DISTINCT p FROM Pedido p JOIN p.itens i WHERE p.status IN (:status) AND i.produto.precisaPreparo = true ORDER BY p.dataHora ASC")
    List<Pedido> findByStatusInOrderByDataHoraAsc(@Param("status") List<StatusPedido> status);

    // 🚀 NOVO KPI DA ESTEIRA: Só conta no Dashboard os pedidos ativos que estão de fato na dependência da cozinha
    @Query("SELECT COUNT(DISTINCT p) FROM Pedido p JOIN p.itens i WHERE p.status NOT IN (:finalizado, :cancelado) AND i.produto.precisaPreparo = true")
    long countPedidosAtivos(
            @Param("finalizado") StatusPedido finalizado,
            @Param("cancelado") StatusPedido cancelado
    );

    // Query de faturamento por modalidade (permanece igual)
    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p WHERE p.dataHora >= :inicio AND p.status = :status AND CAST(p.formaPagamento AS string) = :forma")
    java.math.BigDecimal somarFaturamentoPorTurnoEForma(
            @Param("inicio") java.time.LocalDateTime inicio,
            @Param("forma") String forma,
            @Param("status") StatusPedido status
    );
}