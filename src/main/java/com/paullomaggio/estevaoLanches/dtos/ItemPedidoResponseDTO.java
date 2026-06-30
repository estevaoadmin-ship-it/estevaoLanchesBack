package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Schema(description = "Resposta contendo os detalhes de um item de pedido")
public record ItemPedidoResponseDTO(
        @Schema(
            description = "Identificador único do item de pedido",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
            description = "ID do produto associado a este item",
            format = "uuid",
            example = "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
        )
        UUID produtoId,
        @Schema(
            description = "Nome do produto",
            example = "Hambúrguer Clássico"
        )
        String produtoNome,
        @Schema(
            description = "Quantidade do produto neste item",
            example = "2"
        )
        Integer quantidade,
        @Schema(
            description = "Preço unitário do produto no momento do pedido",
            example = "25.50"
        )
        BigDecimal precoUnitarioHistorico,
        @Schema(
            description = "Observações específicas para este item",
            example = "Sem picles"
        )
        String observacaoItem,
        @Schema(description = "Lista de adicionais aplicados a este item")
        List<ItemPedidoResponseDTO.AdicionalInfo> adicionais,
        @Schema(
            description = "Número da conta a qual este item pertence (para pedidos de mesa)",
            example = "1"
        )
        Integer numeroConta,
        @Schema(
            description = "Status de pagamento do item",
            allowableValues = {"ABERTO", "PAGO", "CANCELADO"},
            example = "PAGO"
        )
        String statusPagamento,
        @Schema(
            description = "Status de envio/preparo do item",
            allowableValues = {"AGUARDANDO_ENVIO", "ENVIADO"},
            example = "AGUARDANDO_ENVIO"
        )
        String statusEnvio,
        @Schema(
            description = "Indica se o item já foi enviado/preparado",
            example = "false"
        )
        boolean enviado
) {

    @Schema(description = "Informações de um adicional aplicado a um item de pedido")
    public record AdicionalInfo(
            @Schema(
                description = "ID do adicional",
                format = "uuid",
                example = "c3d4e5f6-a7b8-9012-3456-7890abcdef12"
            )
            UUID id,
            @Schema(
                description = "Nome do adicional",
                example = "Bacon Extra"
            )
            String nome,
            @Schema(
                description = "Preço do adicional",
                example = "5.00"
            )
            BigDecimal preco
    ) {}

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