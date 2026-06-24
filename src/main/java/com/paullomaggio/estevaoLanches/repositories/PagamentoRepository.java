package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    /**
     * 🎯 CONSULTA REESTRUTURADA: Consolida a somatória de todas as amortizações
     * utilizando a chave relacional id da Conta mestre.
     */
    @Query("SELECT COALESCE(SUM(p.valorPago), 0) FROM Pagamento p WHERE p.conta.id = :contaId")
    BigDecimal sumPagamentosPorConta(@Param("contaId") UUID contaId);

    List<Pagamento> findByContaId(UUID contaId);
}