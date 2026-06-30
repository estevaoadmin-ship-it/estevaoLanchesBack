package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ContaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ContaResponseDTO;
import com.paullomaggio.estevaoLanches.services.ContaService;
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
@RequestMapping("/api/contas")
@CrossOrigin(origins = "*")
@Tag(name = "Contas", description = "Operações relacionadas à gestão de contas de pedidos")
public class ContaController {

    @Autowired private ContaService contaService;

    @Operation(summary = "Cria uma nova conta para um pedido",
               description = "Cria uma nova conta associada a um pedido, permitindo pagamentos fracionados.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Conta criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da conta inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping
    public ResponseEntity<ContaResponseDTO> criar(@Valid @RequestBody ContaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contaService.criar(dto));
    }

    @Operation(summary = "Busca uma conta pelo ID",
               description = "Retorna os detalhes de uma conta específica com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conta encontrada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ContaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(contaService.buscarPorId(id));
    }

    /**
     * 🎯 REAJUSTADO: Endpoint mapeia a listagem das contas a partir da comanda mãe.
     */
    @Operation(summary = "Lista contas associadas a uma comanda",
               description = "Retorna todas as contas (fracionadas ou principal) vinculadas a uma comanda específica.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de contas retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/comanda/{comandaId}")
    public ResponseEntity<List<ContaResponseDTO>> listarPorComanda(@PathVariable UUID comandaId) {
        return ResponseEntity.ok(contaService.listarPorComanda(comandaId));
    }

    @Operation(summary = "Liquida uma conta (marca como paga)",
               description = "Marca uma conta específica como paga, encerrando seu ciclo financeiro.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conta liquidada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PutMapping("/{id}/pagar")
    public ResponseEntity<ContaResponseDTO> liquidarConta(@PathVariable UUID id) {
        return ResponseEntity.ok(contaService.liquidarConta(id));
    }

    @Operation(summary = "Deleta uma conta",
               description = "Remove uma conta do sistema. Esta operação geralmente é restrita e usada para correção de erros.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Conta deletada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        contaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}