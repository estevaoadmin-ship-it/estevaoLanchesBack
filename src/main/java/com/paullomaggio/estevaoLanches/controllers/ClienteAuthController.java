package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.AuthResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.GoogleLoginRequestDTO;
import com.paullomaggio.estevaoLanches.services.ClienteAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/cliente")
@Tag(name = "Autenticação de Cliente", description = "Operações de autenticação específicas para clientes, incluindo login com Google.")
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;

    public ClienteAuthController(ClienteAuthService clienteAuthService) {
        this.clienteAuthService = clienteAuthService;
    }

    @Operation(summary = "Autentica um cliente usando uma conta Google",
               description = "Permite que clientes façam login ou registrem-se utilizando suas credenciais Google, fornecendo um ID Token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticação Google bem-sucedida, token JWT retornado"),
            @ApiResponse(responseCode = "401", description = "Falha na autenticação com o Google ou ID Token inválido")
    })
    @PostMapping("/google")
    public ResponseEntity<?> loginGoogle(@RequestBody GoogleLoginRequestDTO request) {
        try {
            String jwtToken = clienteAuthService.autenticarComGoogle(request.idToken());
            return ResponseEntity.ok(new AuthResponseDTO(jwtToken, "Cliente", "CLIENTE"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Falha na autenticação com o Google: " + e.getMessage());
        }
    }
}