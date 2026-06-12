package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnderecoRequestDTO(
        @NotBlank(message = "O rótulo do endereço é obrigatório (ex: Casa, Trabalho).")
        String rotulo,

        @NotBlank(message = "O logradouro é obrigatório.")
        String logradouro,

        @NotBlank(message = "O número é obrigatório.")
        String numero,

        String complemento,

        @NotBlank(message = "O bairro é obrigatório.")
        String bairro,

        @NotBlank(message = "A cidade é obrigatória.")
        String city, // mapeado como cidade na entidade

        @NotBlank(message = "A UF é obrigatória.")
        @Size(min = 2, max = 2, message = "A UF deve ter exatamente 2 caracteres.")
        String uf,

        String cep
) {}