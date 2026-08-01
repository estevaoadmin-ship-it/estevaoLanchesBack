package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GarcomMesaSessaoResponseDTO(
        UUID mesaId,
        Integer numeroMesa,
        StatusMesa statusMesa,
        UUID comandaId,
        StatusComanda statusComanda,
        LocalDateTime abertaEm,
        UUID contaSelecionadaId,
        List<ContaSessaoDTO> contas
) {
    public record ContaSessaoDTO(
            UUID id,
            Integer numeroConta,
            StatusPagamento statusConta,
            BigDecimal valorTotal,
            Boolean isSelecionada,
            ClienteSessaoDTO cliente,
            List<ItemSessaoDTO> itens,
            List<PagamentoSessaoDTO> pagamentos
    ) {}

    public record ClienteSessaoDTO(
            UUID id,
            String nome,
            String telefone
    ) {}

    public record ItemSessaoDTO(
            UUID id,
            UUID pedidoId,
            String numeroPedido,
            StatusPedido statusPedido,
            StatusFinanceiro statusFinanceiro,
            TipoPedido tipoPedido,
            UUID produtoId,
            String nomeProduto,
            Integer quantidade,
            BigDecimal valorUnitario,
            BigDecimal valorTotal,
            String observacao,
            Boolean precisaPreparo,
            Boolean enviado,
            List<AdicionalDTO> adicionais
    ) {}

    public record AdicionalDTO(
            UUID id,
            String nome,
            BigDecimal preco
    ) {}
}