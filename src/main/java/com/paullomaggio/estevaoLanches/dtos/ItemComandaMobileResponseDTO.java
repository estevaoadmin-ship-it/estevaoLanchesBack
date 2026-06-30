package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Resposta contendo os detalhes de um item de comanda para exibição em dispositivos móveis")
public record ItemComandaMobileResponseDTO(
        @Schema(
            description = "ID do produto",
            format = "uuid",
            example = "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
        )
        UUID produtoId,
        @Schema(
            description = "Nome do produto",
            example = "Hambúrguer Clássico"
        )
        String nome,
        @Schema(
            description = "Quantidade do produto",
            example = "1"
        )
        int quantidade,
        @Schema(
            description = "Preço calculado do item (quantidade * preço unitário)",
            example = "29.90"
        )
        BigDecimal precoCalculado,
        @Schema(
            description = "Observações específicas para este item",
            example = "Sem picles"
        )
        String observacao,
        @Schema(
            description = "Número da conta a qual este item pertence",
            example = "1"
        )
        Integer numeroConta,
        @Schema(description = "Lista de adicionais aplicados a este item")
        List<Adicional> adicionais,
        @Schema(description = "Informações do cliente associado a este item (se aplicável)")
        ClienteMesaDTO cliente,
        @Schema(
            description = "ID da comanda a qual este item pertence",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID comandaId
) {
    @Schema(description = "Informações básicas do cliente para itens de comanda")
    public record ClienteMesaDTO(
            @Schema(
                description = "Nome do cliente",
                example = "João da Silva"
            )
            String nome,
            @Schema(
                description = "Telefone do cliente",
                example = "5511987654321"
            )
            String telefone
    ) {}
}