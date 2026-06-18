package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.services.CaixaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal; // 👈 IMPORTAÇÃO QUE FALTAVA CORRIGIDA AQUI
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/caixas")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class CaixaController {

    @Autowired
    private CaixaService caixaService;

    @GetMapping("/status")
    public ResponseEntity<CaixaStatusResponseDTO> verificarStatus() {
        Optional<CaixaStatusResponseDTO> status = caixaService.obterStatusAtual();
        return status.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build()); // Retorna 200 OK sem corpo (null) se fechado
    }

    @GetMapping("/resumo")
    public ResponseEntity<CaixaResumoResponseDTO> obterResumo() {
        return ResponseEntity.ok(caixaService.obterResumoTurno());
    }

    @PostMapping
    public ResponseEntity<CaixaStatusResponseDTO> abrirCaixa(@RequestBody @Valid CaixaAberturaRequestDTO dto) {
        CaixaStatusResponseDTO caixaAberto = caixaService.abrirCaixa(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(caixaAberto);
    }

    @PatchMapping("/ativo")
    public ResponseEntity<Map<String, String>> fecharCaixa(@RequestBody @Valid CaixaFechamentoRequestDTO dto) {
        caixaService.fecharCaixa(dto);
        return ResponseEntity.ok(Map.of("message", "Turno encerrado e auditoria salva com sucesso!"));
    }

    // ==========================================
    // 🚀 ROTAS DO LIVRO-RAZÃO E AUDITORIA
    // ==========================================

    @PostMapping("/sangria")
    public ResponseEntity<Map<String, String>> lancarSangria(@RequestBody @Valid MovimentacaoRequestDTO dto) {
        caixaService.lancarSangria(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Sangria registrada com sucesso!"));
    }

    @PostMapping("/suprimento")
    public ResponseEntity<Map<String, String>> lancarSuprimento(@RequestBody @Valid MovimentacaoRequestDTO dto) {
        caixaService.lancarSuprimento(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Suprimento registrado com sucesso!"));
    }

    @PutMapping("/movimentacoes/{id}/estorno")
    public ResponseEntity<Map<String, String>> estornarMovimentacao(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload) {

        String motivo = payload.get("motivoEstorno");
        caixaService.estornarMovimentacao(id, motivo);
        return ResponseEntity.ok(Map.of("message", "Movimentação estornada e auditada com sucesso!"));
    }

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

    @GetMapping("/pedido/{pedidoId}/conta/{numeroConta}/saldo")
    public ResponseEntity<BigDecimal> obterSaldo(@PathVariable UUID pedidoId, @PathVariable Integer numeroConta) {
        return ResponseEntity.ok(caixaService.calcularSaldoDevedorDaConta(pedidoId, numeroConta));
    }

    @PostMapping("/pedido/{pedidoId}/pagar-fracionado")
    public ResponseEntity<Map<String, String>> pagarFracionado(
            @PathVariable UUID pedidoId,
            @RequestBody @Valid ContaPagamentoRequestDTO dto) {
        caixaService.registrarPagamentoFracionado(pedidoId, dto);
        return ResponseEntity.ok(Map.of("message", "Pagamento processado com sucesso!"));
    }
}