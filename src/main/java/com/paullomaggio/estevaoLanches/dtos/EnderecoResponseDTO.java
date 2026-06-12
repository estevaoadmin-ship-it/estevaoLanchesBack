package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Endereco;
import java.util.UUID;

public record EnderecoResponseDTO(
        UUID id,
        String rotulo,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep
) {
    public EnderecoResponseDTO(Endereco endereco) {
        this(
                endereco.getId(),
                endereco.getRotulo(),
                endereco.getLogradouro(),
                endereco.getNumero(),
                endereco.getComplemento(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getUf(),
                endereco.getCep()
        );
    }
}