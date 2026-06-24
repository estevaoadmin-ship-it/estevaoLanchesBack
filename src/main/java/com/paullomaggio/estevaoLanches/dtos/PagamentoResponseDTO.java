package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoResponseDTO(
        UUID id,
        UUID contaId,
        Integer numeroConta,
        BigDecimal valorPago,
        FormaPagamento formaPagamento,
        LocalDateTime dataHora,
        String usuarioResponsavel
) {
    public PagamentoResponseDTO(Pagamento pagamento) {
        this(
                pagamento.getId(),
                pagamento.getConta() != null ? pagamento.getConta().getId() : null,
                pagamento.getConta() != null ? pagamento.getConta().getNumeroConta() : null,
                pagamento.getValorPago(),
                pagamento.getFormaPagamento(),
                pagamento.getDataHora(),
                pagamento.getUsuarioResponsavel()
        );
    }
}