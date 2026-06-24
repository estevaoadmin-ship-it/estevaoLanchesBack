package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.enums.StatusEnvioItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, UUID> {

    List<ItemPedido> findByPedidoId(UUID pedidoId);

    List<ItemPedido> findByPedidoIdAndNumeroConta(UUID pedidoId, Integer numeroConta);

    List<ItemPedido> findByPedidoIdAndStatusEnvio(UUID pedidoId, StatusEnvioItem statusEnvio);
}