package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    /**
     * 🛡️ LOCK PESSIMISTA DE ESCRITA:
     * Bloqueia a linha no PostgreSQL impedindo condições de corrida no fechamento do caixa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findByIdForUpdate(@Param("id") UUID id);

    /**
     * PERFORMANCE FIX:
     * Busca no banco estritamente os pedidos que pertencem à lista de contas daquela mesa.
     */
    List<Pedido> findByContaIdIn(Collection<UUID> contaIds);

    /**
     * 🎯 RESTAURAÇÃO DE TESTE (Erros 1 e 2):
     * Busca o histórico completo de pedidos de um cliente ordenado por data decrescente.
     */
    List<Pedido> findByClienteIdOrderByDataHoraDesc(UUID clienteId);

    /**
     * 🚚 DELIVERY APP E SEPARAÇÃO DE CONTEXTOS:
     * Busca o histórico de pedidos de um cliente filtrado exclusivamente pelo canal de venda (ex: DELIVERY).
     */
    List<Pedido> findByClienteIdAndTipo(UUID clienteId, TipoPedido tipo);

    /**
     * 🎯 RESTAURAÇÃO DE TESTE (Erros 3 e 4):
     * Filtra lotes de pedidos contidos em uma lista de status operacionais com ordenação ascendente.
     */
    List<Pedido> findByStatusInOrderByDataHoraAsc(List<StatusPedido> statuses);

    /**
     * 🎯 RESTAURAÇÃO DE TESTE (Erros 5 e 6):
     * Localiza um lote de pedidos através do seu número único e imutável gerado no PrePersist.
     */
    Optional<Pedido> findByNumeroPedido(String numeroPedido);

    /**
     * 🎯 RESTAURAÇÃO DE TESTE (Erro 7):
     * Consolida o somatório de faturamento de uma forma de pagamento a partir da abertura do turno.
     */
    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p WHERE p.dataHora >= :desde AND p.formaPagamento = :forma AND p.status = :status")
    BigDecimal somarFaturamentoPorTurnoEForma(@Param("desde") LocalDateTime desde, @Param("forma") FormaPagamento forma, @Param("status") StatusPedido status);

    /**
     * REGRA DE NEGÓCIO CAIXA SERVICE:
     * Conta a quantidade de pedidos ativos no sistema para impedir o fechamento de caixas pendentes.
     */
    @Query("SELECT COUNT(p) FROM Pedido p WHERE p.status <> :status1 AND p.status <> :status2")
    long countPedidosAtivos(@Param("status1") StatusPedido status1, @Param("status2") StatusPedido status2);

    /**
     * REGRA DE NEGÓCIO RELATÓRIO SERVICE:
     * Executa a agregação de faturamento por meio de pagamento projetando diretamente no DTO.
     */
    @Query("SELECT new com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO(p.formaPagamento, SUM(p.total)) " +
            "FROM Pedido p WHERE p.dataHora BETWEEN :inicio AND :fim AND p.status = :status GROUP BY p.formaPagamento")
    List<MeioPagamentoItemDTO> somarFaturamentoPorMeioPagamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, @Param("status") StatusPedido status);

    /**
     * REGRA DE NEGÓCIO RELATÓRIO SERVICE:
     * Ranking de produtos JPQL paginado de acordo com os parâmetros enviados pela esteira de relatórios.
     */
    @Query("SELECT new com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO(ip.produto.nome, COUNT(ip.id)) " +
            "FROM ItemPedido ip WHERE ip.pedido.dataHora BETWEEN :inicio AND :fim AND ip.pedido.status = :status " +
            "GROUP BY ip.produto.nome ORDER BY COUNT(ip.id) DESC")
    List<ProdutoRankingDTO> buscarTopProdutosJPQL(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, @Param("status") StatusPedido status, Pageable pageable);

    /**
     * Busca os lotes de pedidos compreendidos no intervalo selecionado para relatórios gerais.
     */
    @Query("SELECT p FROM Pedido p WHERE p.dataHora BETWEEN :inicio AND :fim")
    List<Pedido> buscarPedidosParaRelatorio(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}