package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import com.paullomaggio.estevaoLanches.services.ContaDeliveryService;
import com.paullomaggio.estevaoLanches.services.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AutenticacaoController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final ContaDeliveryService contaDeliveryService;
    private final ContaDeliveryRepository contaDeliveryRepository;
    private final PasswordEncoder passwordEncoder;

    // 🎯 INJEÇÃO POR CONSTRUTOR: Elimina os @Autowired avulsos e garante acoplamento seguro em ambiente de teste
    public AutenticacaoController(AuthenticationManager authenticationManager,
                                  TokenService tokenService,
                                  ContaDeliveryService contaDeliveryService,
                                  ContaDeliveryRepository contaDeliveryRepository,
                                  PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.contaDeliveryService = contaDeliveryService;
        this.contaDeliveryRepository = contaDeliveryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 🖥️ ROTA PDV WEB CENTRAL: Autenticação de Funcionários (Blindada contra 403 indesejados)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        try {
            var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.senha());
            var auth = this.authenticationManager.authenticate(usernamePassword);

            var usuario = (Usuario) auth.getPrincipal();
            var token = tokenService.gerarToken(usuario);

            return ResponseEntity.ok(new LoginResponseDTO(token, new UsuarioResponseDTO(usuario)));

        } catch (AuthenticationException e) {
            // 🎯 CAPTURA DA BARREIRA: Captura falhas de credenciais e força o retorno de 401 Unauthorized limpo
            // Isso satisfaz o critério do seu teste de segurança e protege as rotas gerenciais
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    // 🚀 ROTA DELIVERY APP: Cadastro nativo por e-mail e senha (Fase 1)
    @PostMapping("/registrar")
    public ResponseEntity<String> registrarCliente(@Valid @RequestBody RegistroDeliveryRequestDTO dto) {
        contaDeliveryService.registrarNovaConta(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Conta de Delivery cadastrada com sucesso!");
    }

    // 🚀 ROTA DELIVERY APP: Login nativo por credenciais digitais de cliente
    @PostMapping("/login/cliente")
    public ResponseEntity<?> loginCliente(@RequestBody LoginRequestDTO dto) {
        if (dto.email() == null || dto.senha() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("E-mail e senha são obrigatórios.");
        }

        ContaDelivery conta = contaDeliveryRepository.findByEmail(dto.email().trim().toLowerCase())
                .orElse(null);

        if (conta == null || !passwordEncoder.matches(dto.senha(), conta.getSenha())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("E-mail ou senha incorretos.");
        }

        if (!conta.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Esta conta está inativa no sistema.");
        }

        String token = tokenService.gerarTokenCliente(conta);
        ClienteLoginResponseDTO response = new ClienteLoginResponseDTO(
                token,
                conta.getCliente().getNome(),
                conta.getEmail(),
                conta.getRole()
        );

        return ResponseEntity.ok(response);
    }
}