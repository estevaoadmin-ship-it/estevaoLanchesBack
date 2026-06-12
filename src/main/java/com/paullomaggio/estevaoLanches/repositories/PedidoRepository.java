package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    // Histórico do cliente (ordenado do mais recente para o mais antigo)
    List<Pedido> findByClienteIdOrderByDataHoraDesc(UUID clienteId);

    // Painel da Cozinha e Dashboard (Busca pedidos ativos)
    List<Pedido> findByStatusInOrderByDataHoraAsc(List<StatusPedido> status);

    // Busca rápida pelo código do cupom/painel
    Optional<Pedido> findByNumeroPedido(String numeroPedido);
}