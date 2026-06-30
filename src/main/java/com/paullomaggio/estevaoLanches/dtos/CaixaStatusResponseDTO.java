package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Resposta contendo o status atual do caixa")
public record CaixaStatusResponseDTO(
        @Schema(
            description = "Indica se o caixa está aberto",
            example = "true"
        )
        boolean aberto,
        @Schema(
            description = "Status detalhado do caixa",
            allowableValues = {"ABERTO", "FECHADO"},
            example = "ABERTO"
        )
        StatusCaixa status,
        @Schema(
            description = "Valor com o qual o caixa foi aberto",
            example = "100.00"
        )
        BigDecimal valorAbertura,
        @Schema(
            description = "Data e hora de abertura do caixa",
            example = "2026-06-30T09:00:00"
        )
        LocalDateTime dataHoraAbertura,
        @Schema(
            description = "Nome do usuário que abriu o caixa",
            example = "Admin Estevao Lanches"
        )
        String nomeUsuarioAbertura
) {
    // Construtor compacto para converter a Entidade JPA de forma elegante
    public CaixaStatusResponseDTO(Caixa caixa) {
        this(
                caixa.getStatus() == StatusCaixa.ABERTO,
                caixa.getStatus(),
                caixa.getValorAbertura(),
                caixa.getDataHoraAbertura(),
                caixa.getUsuarioAbertura().getNome()
        );
    }
}