package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * DTO para representar um item individual de suprimento no histórico de caixa.
 */
@Schema(description = "Item individual de suprimento no histórico de caixa")
public record CaixaSuprimentoItemDTO(
        @Schema(description = "Valor do suprimento", example = "50.00")
        BigDecimal valor,

        @Schema(description = "Descrição do suprimento (contém motivo e detalhes)", example = "COMPRA DE GELO")
        String descricao
) {
}