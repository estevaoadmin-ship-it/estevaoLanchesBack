package com.paullomaggio.estevaoLanches.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class ClienteAuthService {

    @Value("${google.client.id}")
    private String googleClientId;

    private final ClienteRepository clienteRepository;
    private final TokenService tokenService;

    public ClienteAuthService(ClienteRepository clienteRepository, TokenService tokenService) {
        this.clienteRepository = clienteRepository;
        this.tokenService = tokenService;
    }

    public String autenticarComGoogle(String idTokenString) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);

        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String nome = (String) payload.get("name");

            // 🚨 BLINDAGEM: Validação de integridade dos dados vindos do Google
            if (email == null) {
                throw new IllegalArgumentException("E-mail não fornecido pelo Google.");
            }

            // Busca ou Cria o Cliente
            Cliente cliente = clienteRepository.findByEmail(email).orElseGet(() -> {
                Cliente novoCliente = new Cliente();
                novoCliente.setNome(nome);
                novoCliente.setEmail(email);
                return clienteRepository.save(novoCliente);
            });

            // Gera token exclusivo de Cliente
            return tokenService.gerarTokenCliente(cliente);

        } else {
            throw new IllegalArgumentException("Token do Google inválido ou expirado.");
        }
    }
}