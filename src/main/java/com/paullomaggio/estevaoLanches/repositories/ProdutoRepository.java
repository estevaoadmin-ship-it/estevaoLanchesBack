package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    // Traz todos os produtos baseados no status (útil para não mostrar lanches esgotados)
    List<Produto> findByStatus(StatusProduto status);

    // Traz apenas os combos (para uma aba específica no app do delivery)
    List<Produto> findByIsComboTrue();

    // Traz apenas os lanches/bebidas normais (ignora combos)
    List<Produto> findByIsComboFalse();
}
