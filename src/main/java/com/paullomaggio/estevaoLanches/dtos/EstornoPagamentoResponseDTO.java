package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.EstornoPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EstornoPagamentoResponseDTO(

        UUID id,

        UUID pagamentoId,

        UUID caixaId,

        BigDecimal valorEstornado,

        String motivo,

        LocalDateTime dataHora,

        String usuarioResponsavel

) {

    public EstornoPagamentoResponseDTO(EstornoPagamento estorno) {
        this(
                estorno.getId(),
                estorno.getPagamento().getId(),
                estorno.getCaixa().getId(),
                estorno.getValorEstornado(),
                estorno.getMotivo(),
                estorno.getDataHora(),
                estorno.getUsuarioResponsavel()
        );
    }
}
