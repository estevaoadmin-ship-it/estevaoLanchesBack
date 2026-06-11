package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    List<Produto> findByStatus(StatusProduto status);
    List<Produto> findByIsComboTrue();
    List<Produto> findByIsComboFalse();

    @Query("SELECT p FROM Produto p " +
            "JOIN FETCH p.categoria c " +
            "WHERE LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) " +
            "OR LOWER(p.descricao) LIKE LOWER(CONCAT('%', :termo, '%')) " +
            "OR LOWER(c.nome) LIKE LOWER(CONCAT('%', :termo, '%'))")
    List<Produto> buscarPorTermo(@Param("termo") String termo);

    // =========================================================================
    // NOVO: Método que deleta todos os produtos vinculados a uma categoria
    // =========================================================================
    @Modifying
    @Query("DELETE FROM Produto p WHERE p.categoria.id = :categoriaId")
    void deletarPorCategoriaId(@Param("categoriaId") UUID categoriaId);
}