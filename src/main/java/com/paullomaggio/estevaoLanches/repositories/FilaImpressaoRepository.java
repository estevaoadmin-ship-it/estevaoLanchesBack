package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FilaImpressaoRepository extends JpaRepository<FilaImpressao, UUID> {

    // 🚨 FETCH JOIN: Força o carregamento da árvore completa (Pedido -> Itens -> Produto)
    // O 'DISTINCT' impede que o Hibernate duplique a fila caso o pedido tenha muitos itens
    @Query("SELECT DISTINCT f FROM FilaImpressao f " +
            "JOIN FETCH f.pedido p " +
            "LEFT JOIN FETCH p.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "WHERE f.status = :status")
    List<FilaImpressao> findByStatus(@Param("status") FilaImpressao.StatusImpressao status);

}