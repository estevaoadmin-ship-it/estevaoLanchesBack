package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ClienteRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ClienteResponseDTO;
import com.paullomaggio.estevaoLanches.services.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clientes")
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Clientes", description = "Operações relacionadas à gestão de clientes")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

    @Operation(summary = "Lista todos os clientes",
               description = "Retorna uma lista de todos os clientes cadastrados no sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de clientes retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping
    public ResponseEntity<List<ClienteResponseDTO>> listarTodos() {
        return ResponseEntity.ok(clienteService.listarTodos());
    }

    @Operation(summary = "Busca um cliente pelo ID",
               description = "Retorna os detalhes de um cliente específico com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cliente encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @Operation(summary = "Busca um cliente pelo CPF",
               description = "Retorna os detalhes de um cliente específico com base no seu CPF.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cliente encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/cpf/{cpf}")
    public ResponseEntity<ClienteResponseDTO> buscarPorCpf(@PathVariable String cpf) {
        return ResponseEntity.ok(clienteService.buscarPorCpf(cpf));
    }

    @Operation(summary = "Busca clientes por nome",
               description = "Retorna uma lista de clientes que correspondem ao nome fornecido (busca parcial).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de clientes retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteResponseDTO>> buscarPorNome(@RequestParam String nome) {
        return ResponseEntity.ok(clienteService.buscarPorNome(nome));
    }

    @Operation(summary = "Cria um novo cliente",
               description = "Cadastra um novo cliente no sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Cliente criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do cliente inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping
    public ResponseEntity<ClienteResponseDTO> criar(@Valid @RequestBody ClienteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.salvar(dto));
    }

    @Operation(summary = "Atualiza um cliente existente",
               description = "Atualiza os dados de um cliente existente com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cliente atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do cliente inválidos"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponseDTO> atualizar(@PathVariable UUID id, @Valid @RequestBody ClienteRequestDTO dto) {
        return ResponseEntity.ok(clienteService.atualizar(id, dto));
    }

    @Operation(summary = "Exclui um cliente",
               description = "Remove um cliente do sistema com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Cliente excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        clienteService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}