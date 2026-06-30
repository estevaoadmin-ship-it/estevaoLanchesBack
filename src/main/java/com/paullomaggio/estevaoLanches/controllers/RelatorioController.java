package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.DashboardDataDTO;
import com.paullomaggio.estevaoLanches.services.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Relatórios", description = "Operações para geração de relatórios gerenciais e dashboards")
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    @Operation(summary = "Obtém dados para o dashboard gerencial",
               description = "Gera e retorna dados consolidados para exibição no dashboard, filtrados por período e opcionalmente por usuário.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados do dashboard retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de data inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDataDTO> obterDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @RequestParam(required = false) String usuarioId) {

        DashboardDataDTO dados = relatorioService.gerarDashboard(inicio, fim, usuarioId);
        return ResponseEntity.ok(dados);
    }

    @Operation(summary = "Baixa um relatório gerencial em formato PDF",
               description = "Gera um relatório detalhado em PDF com base em um período e opcionalmente filtrado por usuário, e o disponibiliza para download.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Relatório PDF gerado e baixado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de data inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
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