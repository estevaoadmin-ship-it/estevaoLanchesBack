package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import java.util.UUID;

/**
 * DTO de saída mapeado de forma imutável para transporte seguro de dados à API ou Mobile.
 */
public record MesaResponseDTO(
        UUID id,
        Integer numero,
        StatusMesa status,
        UUID empresaId,
        UUID filialId
) {
    /**
     * Construtor compacto para conversão direta da entidade de banco de dados.
     */
    public MesaResponseDTO(Mesa mesa) {
        this(
                mesa.getId(),
                mesa.getNumero(),
                mesa.getStatus(),
                mesa.getEmpresaId(),
                mesa.getFilialId()
        );
    }
}