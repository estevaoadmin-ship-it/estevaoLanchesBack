package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.services.CaixaService;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/caixas")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Caixa", description = "Operações de gestão do caixa, incluindo abertura, fechamento e movimentações financeiras")
public class CaixaController {

    @Autowired
    private CaixaService caixaService;

    @Operation(summary = "Verifica o status atual do caixa",
               description = "Retorna o status do caixa (aberto/fechado) e informações básicas do turno atual, se houver.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status do caixa retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/status")
    public ResponseEntity<CaixaStatusResponseDTO> verificarStatus() {
        Optional<CaixaStatusResponseDTO> status = caixaService.obterStatusAtual();
        return status.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build()); // Retorna 200 OK sem corpo (null) se fechado
    }

    @Operation(summary = "Obtém o resumo do turno atual do caixa",
               description = "Retorna um resumo detalhado das movimentações financeiras do turno de caixa ativo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resumo do turno retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/resumo")
    public ResponseEntity<CaixaResumoResponseDTO> obterResumo() {
        return ResponseEntity.ok(caixaService.obterResumoTurno());
    }

    @Operation(summary = "Abre um novo turno de caixa",
               description = "Inicia um novo turno de caixa com um valor de abertura.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Caixa aberto com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de abertura inválidos ou caixa já está aberto"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping
    public ResponseEntity<CaixaStatusResponseDTO> abrirCaixa(@RequestBody @Valid CaixaAberturaRequestDTO dto) {
        CaixaStatusResponseDTO caixaAberto = caixaService.abrirCaixa(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(caixaAberto);
    }

    @Operation(summary = "Fecha o turno de caixa ativo",
               description = "Encerra o turno de caixa atual, registrando o valor de fechamento e auditoria.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Caixa fechado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de fechamento inválidos ou caixa não está aberto"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PatchMapping("/ativo")
    public ResponseEntity<Map<String, String>> fecharCaixa(@RequestBody @Valid CaixaFechamentoRequestDTO dto) {
        caixaService.fecharCaixa(dto);
        return ResponseEntity.ok(Map.of("message", "Turno encerrado e auditoria salva com sucesso!"));
    }

    // ==========================================
    // 🚀 ROTAS DO LIVRO-RAZÃO E AUDITORIA
    // ==========================================

    @Operation(summary = "Registra uma sangria no caixa",
               description = "Lança uma retirada de dinheiro do caixa (sangria) com o valor e motivo especificados.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Sangria registrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de movimentação inválidos ou caixa não está aberto"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping("/sangria")
    public ResponseEntity<Map<String, String>> lancarSangria(@RequestBody @Valid MovimentacaoRequestDTO dto) {
        caixaService.lancarSangria(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Sangria registrada com sucesso!"));
    }

    @Operation(summary = "Registra um suprimento no caixa",
               description = "Lança um depósito de dinheiro no caixa (suprimento) com o valor e motivo especificados.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Suprimento registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de movimentação inválidos ou caixa não está aberto"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping("/suprimento")
    public ResponseEntity<Map<String, String>> lancarSuprimento(@RequestBody @Valid MovimentacaoRequestDTO dto) {
        caixaService.lancarSuprimento(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Suprimento registrado com sucesso!"));
    }

    @Operation(summary = "Estorna uma movimentação de caixa",
               description = "Reverte uma movimentação financeira (sangria ou suprimento) previamente registrada no caixa.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Movimentação estornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Motivo de estorno inválido"),
            @ApiResponse(responseCode = "404", description = "Movimentação não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PutMapping("/movimentacoes/{id}/estorno")
    public ResponseEntity<Map<String, String>> estornarMovimentacao(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {

        String motivo = payload.get("motivoEstorno");
        caixaService.estornarMovimentacao(id, motivo);
        return ResponseEntity.ok(Map.of("message", "Movimentação estornada e auditada com sucesso!"));
    }

    @Operation(summary = "Reabre um caixa fechado",
               description = "Permite que um administrador reabra um turno de caixa que foi previamente fechado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Caixa reaberto com sucesso"),
            @ApiResponse(responseCode = "400", description = "Motivo de reabertura inválido ou caixa já está aberto"),
            @ApiResponse(responseCode = "404", description = "Caixa não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PutMapping("/{id}/reabrir")
    public ResponseEntity<Map<String, String>> reabrirCaixa(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {

        String motivo = payload.get("motivoReabertura");
        caixaService.reabrirCaixa(id, motivo);
        return ResponseEntity.ok(Map.of("message", "Caixa reaberto com sucesso pelo Administrador!"));
    }

    // ==========================================
    // 💳 ROTAS DE CONTAS FRACIONADAS
    // ==========================================

    @Operation(summary = "Obtém o saldo devedor de uma conta fracionada de pedido",
               description = "Calcula o saldo restante a ser pago para uma conta fracionada específica de um pedido.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saldo devedor retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pedido ou conta não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/pedido/{pedidoId}/conta/{numeroConta}/saldo")
    public ResponseEntity<BigDecimal> obterSaldo(@PathVariable UUID pedidoId, @PathVariable Integer numeroConta) {
        return ResponseEntity.ok(caixaService.calcularSaldoDevedorDaConta(pedidoId, numeroConta));
    }

    @Operation(summary = "Registra um pagamento fracionado para um pedido",
               description = "Permite registrar um pagamento parcial para um pedido, associado a uma conta fracionada.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pagamento fracionado processado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de pagamento inválidos"),
            @ApiResponse(responseCode = "404", description = "Pedido ou conta não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping("/pedido/{pedidoId}/pagar-fracionado")
    public ResponseEntity<Map<String, String>> pagarFracionado(
            @PathVariable UUID pedidoId,
            @RequestBody @Valid ContaPagamentoRequestDTO dto) {
        caixaService.registrarPagamentoFracionado(pedidoId, dto);
        return ResponseEntity.ok(Map.of("message", "Pagamento processado com sucesso!"));
    }
}