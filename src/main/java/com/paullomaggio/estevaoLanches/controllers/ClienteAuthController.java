package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.AuthResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.GoogleLoginRequestDTO;
import com.paullomaggio.estevaoLanches.services.ClienteAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/cliente")
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;

    public ClienteAuthController(ClienteAuthService clienteAuthService) {
        this.clienteAuthService = clienteAuthService;
    }

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