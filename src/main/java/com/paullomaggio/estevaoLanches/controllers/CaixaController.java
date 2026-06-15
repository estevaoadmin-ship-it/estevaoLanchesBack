package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CaixaAberturaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaFechamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaResumoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaStatusResponseDTO;
import com.paullomaggio.estevaoLanches.services.CaixaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/caixas")
public class CaixaController {

    @Autowired
    private CaixaService caixaService;

    @GetMapping("/status")
    public ResponseEntity<?> verificarStatus() {
        return caixaService.obterStatusAtual()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok().body(null));
    }

    // 🚀 NOVO ENDPOINT: Expõe o painel de KPIs financeiros fatiados para o Angular
    @GetMapping("/resumo")
    public ResponseEntity<CaixaResumoResponseDTO> obterResumo() {
        return ResponseEntity.ok(caixaService.obterResumoTurno());
    }

    @PostMapping
    public ResponseEntity<CaixaStatusResponseDTO> abrir(@RequestBody @Valid CaixaAberturaRequestDTO dto) {
        CaixaStatusResponseDTO response = caixaService.abrirCaixa(dto);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/ativo")
    public ResponseEntity<?> fechar(@RequestBody @Valid CaixaFechamentoRequestDTO dto) {
        caixaService.fecharCaixa(dto);
        return ResponseEntity.ok().body(Map.of("message", "Turno encerrado e salvo com sucesso!"));
    }
}