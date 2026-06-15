package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.DashboardDataDTO;
import com.paullomaggio.estevaoLanches.services.RelatorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/relatorios")
@CrossOrigin(origins = "*") // 🚀 Garante que o Angular consiga ler sem travar no CORS
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDataDTO> obterDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @RequestParam(required = false) String usuarioId) {

        DashboardDataDTO dados = relatorioService.gerarDashboard(inicio, fim, usuarioId);
        return ResponseEntity.ok(dados);
    }

    // 🚀 AQUI ESTÁ O PORTÃO QUE ESTAVA DANDO 404! Mapeado exatamente como '/pdf'
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> baixarRelatorioPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @RequestParam(required = false) String usuarioId) {

        byte[] pdfBytes = relatorioService.exportarRelatorioPdf(inicio, fim, usuarioId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // Define o comportamento de download nativo com o cabeçalho correto para o navegador
        headers.setContentDispositionFormData("attachment", "Relatorio_Gerencial.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}