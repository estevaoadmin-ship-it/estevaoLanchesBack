package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Resposta contendo os detalhes de um adicional de produto")
public record AdicionalResponseDTO(
        @Schema(
            description = "Identificador único do adicional",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
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
) {
    public AdicionalResponseDTO(Adicional adicional) {
        this(adicional.getId(), adicional.getNome(), adicional.getPreco());
    }
}