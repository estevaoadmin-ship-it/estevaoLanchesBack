package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ItemComandaMobileResponseDTO(
        UUID produtoId,
        String nome,
        int quantidade,
        BigDecimal precoCalculado,
        String observacao,
        Integer numeroConta,
        List<Adicional> adicionais,
        ClienteMesaDTO cliente,
        UUID comandaId
) {
    public record ClienteMesaDTO(String nome, String telefone) {}
}