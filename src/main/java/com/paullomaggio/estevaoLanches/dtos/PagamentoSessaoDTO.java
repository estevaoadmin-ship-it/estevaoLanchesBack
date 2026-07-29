package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoSessaoDTO(
    UUID id,
    FormaPagamento formaPagamento,
    BigDecimal valorPago,
    LocalDateTime dataHora,
    String usuarioResponsavel
) {}
