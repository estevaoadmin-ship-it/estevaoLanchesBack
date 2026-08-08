package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * DTO para representar um item individual de sangria no histórico de caixa.
 */
@Schema(description = "Item individual de sangria no histórico de caixa")
public record CaixaSangriaItemDTO(
        @Schema(description = "Valor da sangria", example = "10.00")
        BigDecimal valor,

        @Schema(description = "Descrição da sangria (contém motivo e detalhes)", example = "OUTROS MOTIVOS (AUDITÁVEIS) - BEBIDAS")
        String descricao
) {
}