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

    boolean existsByPedidoIdAndDestino(UUID pedidoId, FilaImpressao.DestinoImpressao destino);

    // 🎯 O EXCLUSOR DE CONCORRÊNCIA: Filtra na raiz do banco e entrega os dados APENAS para a Cozinha
    // Remove o Caixa como consumidor de concorrência física e blinda contra cupons em dobro
    @Query("SELECT DISTINCT f FROM FilaImpressao f " +
            "JOIN FETCH f.pedido p " +
            "LEFT JOIN FETCH p.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "WHERE f.status = :status " +
            "AND f.destino = com.paullomaggio.estevaoLanches.entities.FilaImpressao.DestinoImpressao.COZINHA")
    List<FilaImpressao> findByStatus(@Param("status") FilaImpressao.StatusImpressao status);
}