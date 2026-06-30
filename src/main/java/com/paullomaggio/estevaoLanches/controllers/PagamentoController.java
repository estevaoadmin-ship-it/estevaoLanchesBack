package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.services.PagamentoService;
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
@RequestMapping("/api/pagamentos")
@CrossOrigin(origins = "*")
@Tag(name = "Pagamentos", description = "Operações relacionadas ao registro e consulta de pagamentos")
public class PagamentoController {

    @Autowired private PagamentoService pagamentoService;

    @Operation(summary = "Registra um pagamento para uma conta específica",
               description = "Processa e registra um pagamento recebido para uma conta de pedido, atualizando o saldo da conta.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pagamento registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de pagamento inválidos ou conta já liquidada"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping("/conta/{contaId}")
    public ResponseEntity<PagamentoResponseDTO> receberPagamento(
            @PathVariable UUID contaId,
            @Valid @RequestBody PagamentoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoService.registrarPagamento(contaId, dto));
    }

    @Operation(summary = "Lista todos os pagamentos de uma conta",
               description = "Retorna uma lista de todos os pagamentos registrados para uma conta de pedido específica.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pagamentos retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/conta/{contaId}")
    public ResponseEntity<List<PagamentoResponseDTO>> listarPorConta(@PathVariable UUID contaId) {
        return ResponseEntity.ok(pagamentoService.listarPorConta(contaId));
    }
}