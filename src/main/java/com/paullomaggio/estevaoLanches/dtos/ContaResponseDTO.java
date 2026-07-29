package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Conta;
import java.math.BigDecimal;
import java.util.UUID;

public record ContaResponseDTO(
        UUID id,
        Integer numeroConta,
        Boolean pago,           // ← Mude para boolean (primitivo)
        BigDecimal valorTotal,
        UUID comandaId,
        Integer numeroMesa,
        UUID clienteId,
        String clienteNome
) {
    public ContaResponseDTO(Conta conta) {
        this(
                conta.getId(),
                conta.getNumeroConta(),
                conta.getPago(),                    // ← Agora retorna boolean
                conta.getValorTotal(),
                conta.getComanda() != null ? conta.getComanda().getId() : null,
                (conta.getComanda() != null && conta.getComanda().getMesa() != null)
                        ? conta.getComanda().getMesa().getNumero() : null,
                conta.getCliente() != null ? conta.getCliente().getId() : null,
                conta.getCliente() != null ? conta.getCliente().getNome() : null
        );
    }
}
