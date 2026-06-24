package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.ComboProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ComboProdutoRepository extends JpaRepository<ComboProduto, UUID> {

    List<ComboProduto> findByComboId(UUID comboId);

    void deleteByComboId(UUID comboId);
}