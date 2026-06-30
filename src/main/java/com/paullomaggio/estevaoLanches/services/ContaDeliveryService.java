package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.RegistroDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
public class ContaDeliveryService {

    private final ContaDeliveryRepository contaDeliveryRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    // Injeção de dependência via construtor
    public ContaDeliveryService(ContaDeliveryRepository contaDeliveryRepository,
                                ClienteRepository clienteRepository,
                                PasswordEncoder passwordEncoder) {
        this.contaDeliveryRepository = contaDeliveryRepository;
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void registrarNovaConta(RegistroDeliveryRequestDTO dto) {
        if (dto.email() == null || dto.telefone() == null) {
            throw new BusinessRuleException("E-mail e telefone são obrigatórios para o cadastro.");
        }

        // 🎯 FIX C: Normalização executada no topo absoluto para evitar quebras de Stub/Match
        String emailLimpo = dto.email().trim().toLowerCase();
        String telefoneLimpo = dto.telefone().replaceAll("\\D", "");

        if (contaDeliveryRepository.existsByEmail(emailLimpo)) {
            throw new BusinessRuleException("Este endereço de e-mail já está cadastrado no sistema.");
        }

        // 🎯 FIX B: Localiza ou cria a Ficha Comercial
        Cliente clienteComercial = clienteRepository.findByNumero(telefoneLimpo)
                .orElseGet(() -> {
                    Cliente novoCliente = new Cliente();
                    novoCliente.setNome(dto.nome().trim().toUpperCase());
                    novoCliente.setNumero(telefoneLimpo);
                    novoCliente.setEmail(emailLimpo);
                    novoCliente.setEnderecos(new ArrayList<>());
                    return clienteRepository.save(novoCliente);
                });

        // Atualiza explicitamente o e-mail comercial antigo para bater com as credenciais do app
        clienteComercial.setEmail(emailLimpo);

        ContaDelivery novaConta = new ContaDelivery();
        novaConta.setEmail(emailLimpo);
        novaConta.setSenha(passwordEncoder.encode(dto.senha()));
        novaConta.setAtivo(true);
        novaConta.setRole("ROLE_CLIENTE");
        novaConta.setCliente(clienteComercial);

        contaDeliveryRepository.save(novaConta);
    }
}