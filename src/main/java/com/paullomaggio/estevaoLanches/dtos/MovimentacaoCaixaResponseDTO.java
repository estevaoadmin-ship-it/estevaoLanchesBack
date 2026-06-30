package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.MovimentacaoCaixa;
import com.paullomaggio.estevaoLanches.enums.TipoMovimentacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Resposta contendo os detalhes de uma movimentação de caixa (sangria ou suprimento)")
public record MovimentacaoCaixaResponseDTO(
        @Schema(
            description = "Identificador único da movimentação",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
            description = "ID do caixa ao qual a movimentação pertence",
            format = "uuid",
            example = "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
        )
        UUID caixaId,
        @Schema(
            description = "Tipo da movimentação (SANGRIA ou SUPRIMENTO)",
            allowableValues = {"SANGRIA", "SUPRIMENTO"},
            example = "SANGRIA"
        )
        TipoMovimentacao tipo,
        @Schema(
            description = "Valor da movimentação",
            example = "50.00"
        )
        BigDecimal valor,
        @Schema(
            description = "Descrição da movimentação",
            example = "Sangria para retirada de dinheiro do caixa"
        )
        String descricao,
        @Schema(
            description = "Data e hora da movimentação",
            example = "2026-06-30T10:30:00"
        )
        LocalDateTime dataHora,
        @Schema(
            description = "Indica se a movimentação foi estornada",
            example = "false"
        )
        Boolean estornada
) {
    public MovimentacaoCaixaResponseDTO(MovimentacaoCaixa movimentacao) {
        this(
                movimentacao.getId(),
                movimentacao.getCaixa() != null ? movimentacao.getCaixa().getId() : null,
                movimentacao.getTipo(),
                movimentacao.getValor(),
                movimentacao.getDescricao(),
                movimentacao.getDataHora(),
                movimentacao.getEstornada()
        );
    }
}