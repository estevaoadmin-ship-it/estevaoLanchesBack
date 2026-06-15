package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    List<Pedido> findByClienteIdOrderByDataHoraDesc(UUID clienteId);

    Optional<Pedido> findByNumeroPedido(String numeroPedido);

    @Query("SELECT DISTINCT p FROM Pedido p JOIN p.itens i WHERE p.status IN (:status) AND i.produto.precisaPreparo = true ORDER BY p.dataHora ASC")
    List<Pedido> findByStatusInOrderByDataHoraAsc(@Param("status") List<StatusPedido> status);

    @Query("SELECT COUNT(DISTINCT p) FROM Pedido p JOIN p.itens i WHERE p.status NOT IN (:finalizado, :cancelado) AND i.produto.precisaPreparo = true")
    long countPedidosAtivos(
            @Param("finalizado") StatusPedido finalizado,
            @Param("cancelado") StatusPedido cancelado
    );

    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p WHERE p.dataHora >= :inicio AND p.status = :status AND p.formaPagamento = :forma")
    java.math.BigDecimal somarFaturamentoPorTurnoEForma(
            @Param("inicio") LocalDateTime inicio,
            @Param("forma") FormaPagamento forma,
            @Param("status") StatusPedido status
    );

    // ==========================================
    // 📊 QUERIES DO RELATÓRIO CORRIGIDAS (SEM FILTRO DE USUÁRIO)
    // ==========================================

    @Query("SELECT p FROM Pedido p WHERE p.dataHora BETWEEN :inicio AND :fim")
    List<Pedido> buscarPedidosParaRelatorio(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("SELECT new com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO(p.formaPagamento, SUM(p.total)) " +
            "FROM Pedido p " +
            "WHERE p.dataHora BETWEEN :inicio AND :fim " +
            "AND p.status = :statusFinalizado " +
            "GROUP BY p.formaPagamento")
    List<MeioPagamentoItemDTO> somarFaturamentoPorMeioPagamento(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("statusFinalizado") StatusPedido statusFinalizado
    );

    @Query("SELECT new com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO(pr.nome, SUM(ip.quantidade)) " +
            "FROM Pedido ped JOIN ped.itens ip JOIN ip.produto pr " +
            "WHERE ped.dataHora BETWEEN :inicio AND :fim " +
            "AND ped.status = :statusFinalizado " +
            "GROUP BY pr.nome " +
            "ORDER BY SUM(ip.quantidade) DESC")
    List<ProdutoRankingDTO> buscarTopProdutosJPQL(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("statusFinalizado") StatusPedido statusFinalizado,
            Pageable pageable
    );
}