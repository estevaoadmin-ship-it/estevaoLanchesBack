package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CaixaAberturaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaFechamentoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.services.CaixaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/caixas")
@CrossOrigin(origins = "http://localhost:4200")
public class CaixaController {

    @Autowired
    private CaixaService caixaService;

    // RESTful: Verifica o status atual (bom para o Angular decidir se mostra aviso)
    @GetMapping("/status")
    public ResponseEntity<Boolean> verificarStatus() {
        return ResponseEntity.ok(caixaService.isCaixaAberto());
    }

    // RESTful: POST cria o recurso do Caixa do dia (Abertura)
    @PostMapping
    public ResponseEntity<Caixa> abrir(@RequestBody CaixaAberturaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caixaService.abrirCaixa(dto));
    }

    // RESTful: PATCH altera parcialmente o recurso do Caixa ativo (Fechamento)
    @PatchMapping("/ativo")
    public ResponseEntity<Caixa> fechar(@RequestBody CaixaFechamentoRequestDTO dto) {
        return ResponseEntity.ok(caixaService.fecharCaixa(dto));
    }
}