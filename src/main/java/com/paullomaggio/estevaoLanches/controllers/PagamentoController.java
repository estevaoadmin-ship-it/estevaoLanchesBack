package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.EstornarPagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.EstornoPagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoPesquisaDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoPesquisaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.TipoPesquisaPagamento;
import com.paullomaggio.estevaoLanches.services.EstornoPagamentoService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pagamentos")
@CrossOrigin(origins = "*")
@Tag(name = "Pagamentos", description = "Operações relacionadas ao registro, consulta e estorno de pagamentos")
public class PagamentoController {

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private EstornoPagamentoService estornoPagamentoService;

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

    @Operation(summary = "Estorna um pagamento existente",
            description = "Realiza o estorno total ou parcial de um pagamento, atualizando o saldo estornável e o status financeiro do pedido/conta, se aplicável.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Estorno registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Regra de negócio violada (ex: valor de estorno inválido, caixa fechado, saldo insuficiente)"),
            @ApiResponse(responseCode = "404", description = "Pagamento não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @PostMapping("/{pagamentoId}/estornos")
    public ResponseEntity<EstornoPagamentoResponseDTO> estornar(
            @PathVariable UUID pagamentoId,
            @Valid @RequestBody EstornarPagamentoRequestDTO dto
    ) {
        EstornoPagamentoResponseDTO response =
                estornoPagamentoService.estornar(
                        pagamentoId,
                        dto
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "Lista todos os estornos de um pagamento",
            description = "Retorna uma lista de todos os estornos registrados para um pagamento específico.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de estornos retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pagamento não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping("/{pagamentoId}/estornos")
    public ResponseEntity<List<EstornoPagamentoResponseDTO>> listarEstornosPorPagamento(
            @PathVariable UUID pagamentoId
    ) {
        List<EstornoPagamentoResponseDTO> response =
                estornoPagamentoService.listarPorPagamento(pagamentoId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lista todos os pagamentos de um pedido",
            description = "Retorna uma lista de todos os pagamentos registrados para um pedido específico.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pagamentos retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<List<PagamentoResponseDTO>> listarPagamentosPorPedido(
            @PathVariable UUID pedidoId
    ) {
        List<PagamentoResponseDTO> response =
                pagamentoService.listarPorPedido(pedidoId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Pesquisa de pagamentos para a tela de Estornos",
            description = "Retorna uma lista de pagamentos com filtros por termo e tipo de pesquisa. " +
                    "Os parâmetros termo e tipo são opcionais. Quando nenhum filtro é fornecido, retorna todos os registros.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pagamentos pesquisada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @GetMapping("/pesquisa")
    public ResponseEntity<List<PagamentoPesquisaDTO>> pesquisarPagamentos(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) TipoPesquisaPagamento tipo
    ) {
        PagamentoPesquisaRequestDTO filtro = new PagamentoPesquisaRequestDTO();

        if (termo != null && tipo != null) {
            switch (tipo) {
                case MESA -> {
                    try {
                        filtro.setNumeroMesa(Integer.parseInt(termo));
                    } catch (NumberFormatException e) {
                        // termo não numérico para MESA — não preencher nenhum filtro
                    }
                }
                case PEDIDO -> filtro.setNumeroPedido(termo);
            }
        }

        List<PagamentoPesquisaDTO> resultado = pagamentoService.pesquisarPagamentos(filtro);
        return ResponseEntity.ok(resultado);
    }
}