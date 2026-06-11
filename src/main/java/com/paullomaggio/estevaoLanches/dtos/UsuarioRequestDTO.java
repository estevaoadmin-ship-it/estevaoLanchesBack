package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.RoleUsuario;

public record UsuarioRequestDTO(
        String nome,
        String email,
        String senha,
        RoleUsuario role
) {}