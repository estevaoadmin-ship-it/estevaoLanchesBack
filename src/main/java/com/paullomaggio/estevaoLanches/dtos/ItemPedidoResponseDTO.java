package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ItemPedidoResponseDTO(
        UUID id,
        UUID produtoId,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitarioHistorico,
        String observacaoItem,
        List<ItemPedidoResponseDTO.AdicionalInfo> adicionais,
        Integer numeroConta,
        String statusPagamento,
        String statusEnvio,        // 🎯 NOVO: Transmite 'AGUARDANDO_ENVIO' ou 'ENVIADO' para o Angular
        boolean enviado            // 🎯 NOVO: Flag boleana direta que o Front lê para pintar de cinza fosco
) {


    public record AdicionalInfo(UUID id, String nome, BigDecimal preco) {}

    public ItemPedidoResponseDTO(ItemPedido item) {
        this(
                item.getId(),
                item.getProduto() != null ? item.getProduto().getId() : null,
                item.getProduto() != null ? item.getProduto().getNome() : "Produto Indefinido",
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getObservacaoItem(),
                item.getAdicionais() != null ? item.getAdicionais().stream()
                        .map(a -> new AdicionalInfo(a.getId(), a.getNome(), a.getPreco()))
                        .collect(Collectors.toList()) : List.of(),
                item.getNumeroConta(),
                item.getStatusPagamento() != null ? item.getStatusPagamento().name() : "ABERTO",
                item.getStatusEnvio() != null ? item.getStatusEnvio().name() : "AGUARDANDO_ENVIO",
                item.getStatusEnvio() == com.paullomaggio.estevaoLanches.enums.StatusEnvioItem.ENVIADO
        );
    }
}