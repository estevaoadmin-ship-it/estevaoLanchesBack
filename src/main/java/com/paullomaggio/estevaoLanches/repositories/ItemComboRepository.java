package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ItemComboRepository extends JpaRepository<ItemCombo, UUID> {

    List<ItemCombo> findByItemPedidoId(UUID itemPedidoId);
}