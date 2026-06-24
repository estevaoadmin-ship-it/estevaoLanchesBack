package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import java.time.LocalDateTime;
import java.util.UUID;

public record ComandaResponseDTO(
        UUID id,
        StatusComanda status,
        LocalDateTime abertaEm,
        LocalDateTime fechadaEm,
        UUID empresaId,
        UUID filialId,
        Integer numeroMesa,
        boolean idJaExistia
) {
    public ComandaResponseDTO(Comanda comanda, boolean idJaExistia) {
        this(
                comanda.getId(),
                comanda.getStatus(),
                comanda.getAbertaEm(),
                comanda.getFechadaEm(),
                comanda.getEmpresaId(),
                comanda.getFilialId(),
                comanda.getMesa() != null ? comanda.getMesa().getNumero() : null,
                idJaExistia
        );
    }
}