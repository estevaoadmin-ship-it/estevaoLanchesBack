package com.paullomaggio.estevaoLanches.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class ClienteAuthService {

    @Value("${google.client.id}")
    private String googleClientId;

    private final ClienteRepository clienteRepository;
    private final TokenService tokenService;
    private final ContaDeliveryRepository contaDeliveryRepository; // 🎯 Transformado em final

    // 🎯 FIX ESTRUTURAL: Construtor unificado impede que o Mockito injete 'null' na esteira de testes automatizados
    public ClienteAuthService(ClienteRepository clienteRepository,
                              TokenService tokenService,
                              ContaDeliveryRepository contaDeliveryRepository) {
        this.clienteRepository = clienteRepository;
        this.tokenService = tokenService;
        this.contaDeliveryRepository = contaDeliveryRepository;
    }

    @Transactional
    public String autenticarComGoogle(String idTokenString) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);

        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String nome = (String) payload.get("name");

            if (email == null) {
                throw new IllegalArgumentException("E-mail não fornecido pelo Google.");
            }

            // 1. Garante a Identidade Comercial (Salão/Balcão/Fidelidade)
            Cliente cliente = clienteRepository.findByEmail(email).orElseGet(() -> {
                Cliente novoCliente = new Cliente();
                novoCliente.setNome(nome);
                novoCliente.setEmail(email);
                return clienteRepository.save(novoCliente);
            });

            // 2. Garante a Identidade Digital Autônoma (Spring Security)
            ContaDelivery conta = contaDeliveryRepository.findByEmail(email).orElseGet(() -> {
                ContaDelivery novaConta = new ContaDelivery();
                novaConta.setEmail(email);
                novaConta.setSenha(""); // Login OAuth do Google não possui hash de senha local
                novaConta.setAtivo(true);
                novaConta.setRole("ROLE_CLIENTE");
                novaConta.setCliente(cliente);
                return contaDeliveryRepository.save(novaConta);
            });

            // 3. Emite o Token passando o UserDetails correto da ContaDelivery
            return tokenService.gerarTokenCliente(conta);

        } else {
            throw new IllegalArgumentException("Token do Google inválido ou expirado.");
        }
    }
}