package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ItemPedidoResponseDTO(
        UUID id,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitarioHistorico,
        String observacaoItem,
        List<ItemPedidoResponseDTO.AdicionalInfo> adicionais,
        Integer numeroConta,      // 👈 Informa ao Caixa/Tablet qual é a conta do item
        String statusPagamento    // 👈 Informa se está ABERTO ou PAGO
) {
    public record AdicionalInfo(UUID id, String nome, BigDecimal preco) {}

    public ItemPedidoResponseDTO(ItemPedido item) {
        this(
                item.getId(),
                item.getProduto().getNome(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getObservacaoItem(),
                item.getAdicionais().stream()
                        .map(a -> new AdicionalInfo(a.getId(), a.getNome(), a.getPreco()))
                        .collect(Collectors.toList()),
                item.getNumeroConta(),
                item.getStatusPagamento() != null ? item.getStatusPagamento().name() : "ABERTO"
        );
    }
}