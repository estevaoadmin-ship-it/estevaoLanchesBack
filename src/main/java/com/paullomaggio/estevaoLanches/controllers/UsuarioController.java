package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.UsuarioRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.UsuarioResponseDTO;
import com.paullomaggio.estevaoLanches.services.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID; // Adicionado import para UUID

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Usuários", description = "Operações relacionadas à gestão de usuários (funcionários) do sistema")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Operation(summary = "Lista todos os usuários",
               description = "Retorna uma lista de todos os usuários (funcionários) cadastrados no sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de usuários retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @Operation(summary = "Cria um novo usuário",
               description = "Cadastra um novo usuário (funcionário) no sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do usuário inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> criar(@RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.salvar(dto));
    }

    @Operation(summary = "Busca um usuário por ID",
               description = "Retorna os detalhes de um usuário (funcionário) específico pelo seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @Operation(summary = "Atualiza um usuário existente",
               description = "Atualiza os dados de um usuário (funcionário) existente pelo seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do usuário inválidos"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizar(@PathVariable UUID id, @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizar(id, dto));
    }

    @Operation(summary = "Inativa um usuário",
               description = "Inativa (soft delete) um usuário (funcionário) pelo seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Usuário inativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        usuarioService.deletarOuInativar(id);
        return ResponseEntity.noContent().build();
    }
}
