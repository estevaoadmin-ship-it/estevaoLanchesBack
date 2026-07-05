package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Resposta contendo os detalhes de uma comanda")
public record ComandaResponseDTO(
        @Schema(
                description = "Identificador único da comanda",
                format = "uuid",
                example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
                description = "Status atual da comanda",
                allowableValues = {"ABERTA", "FECHADA"},
                example = "ABERTA"
        )
        StatusComanda status,
        @Schema(
                description = "Data e hora de abertura da comanda",
                example = "2026-06-30T19:00:00"
        )
        LocalDateTime abertaEm,
        @Schema(
                description = "Data e hora de fechamento da comanda (se aplicável)",
                example = "2026-06-30T21:30:00"
        )
        LocalDateTime fechadaEm,
        @Schema(
                description = "ID da empresa à qual a comanda pertence",
                format = "uuid",
                example = "f1e2d3c4-b5a6-0987-6543-210fedcba987"
        )
        UUID empresaId,
        @Schema(
                description = "ID da filial à qual a comanda pertence",
                format = "uuid",
                example = "c3d4e5f6-a7b8-9012-3456-7890abcdef12"
        )
        UUID filialId,
        @Schema(
                description = "Número da mesa associada à comanda (se aplicável)",
                example = "7"
        )
        Integer numeroMesa,
        @Schema(
                description = "Identificador único (UUID) da mesa fìsica no banco de dados",
                format = "uuid",
                example = "e9r8t7y6-u5i4-o3p2-1q2w-3e4r5t6y7u8i"
        )
        UUID mesaId,
        @Schema(
                description = "Indica se o ID da comanda já existia no momento da criação (para reabertura, por exemplo)",
                example = "false"
        )
        boolean idJaExistia
) {
    /**
     * Construtor de mapeamento de domínio para DTO.
     * Captura de forma segura tanto o número de exibição visual quanto o UUID relacional do banco.
     */
    public ComandaResponseDTO(Comanda comanda, boolean idJaExistia) {
        this(
                comanda.getId(),
                comanda.getStatus(),
                comanda.getAbertaEm(),
                comanda.getFechadaEm(),
                comanda.getEmpresaId(),
                comanda.getFilialId(),
                comanda.getMesa() != null ? comanda.getMesa().getNumero() : null,
                comanda.getMesa() != null ? comanda.getMesa().getId() : null, // Mapeamento oficial e seguro do UUID da Mesa
                idJaExistia
        );
    }
}