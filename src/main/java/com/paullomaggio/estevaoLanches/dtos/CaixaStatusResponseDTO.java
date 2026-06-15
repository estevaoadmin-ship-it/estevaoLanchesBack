package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CaixaStatusResponseDTO(
        boolean aberto,
        StatusCaixa status,
        BigDecimal valorAbertura,
        LocalDateTime dataHoraAbertura,
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