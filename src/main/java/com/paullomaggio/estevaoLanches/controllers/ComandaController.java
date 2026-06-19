package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.entities.Subconta;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import com.paullomaggio.estevaoLanches.repositories.ComandaRepository;
import com.paullomaggio.estevaoLanches.repositories.MesaRepository;
import com.paullomaggio.estevaoLanches.repositories.SubcontaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/comandas")
@CrossOrigin(origins = "*")
public class ComandaController {

    @Autowired private MesaRepository mesaRepository;
    @Autowired private ComandaRepository comandaRepository;
    @Autowired private SubcontaRepository subcontaRepository;

    @PostMapping("/abrir/{numeroMesa}")
    public ResponseEntity<?> abrirComanda(@PathVariable Integer numeroMesa) {

        // 1. Procura a mesa ou cria uma nova de forma dinâmica
        Mesa mesa = mesaRepository.findByNumero(numeroMesa)
                .orElseGet(() -> {
                    Mesa novaMesa = new Mesa();
                    novaMesa.setNumero(numeroMesa);
                    novaMesa.setStatus(StatusMesa.LIVRE);
                    return mesaRepository.save(novaMesa);
                });

        // 2. Verifica se já existe uma comanda ativa (ABERTA) para esta mesa
        Optional<Comanda> comandaExistente = comandaRepository.findByMesaNumeroAndStatus(numeroMesa, StatusComanda.ABERTA);
        if (comandaExistente.isPresent()) {
            return ResponseEntity.ok(comandaExistente.get());
        }

        // 3. Modifica o status da mesa para OCUPADA
        mesa.setStatus(StatusMesa.OCUPADA);
        mesaRepository.save(mesa);

        // 4. Cria a Comanda injetando os UUIDs fixos (Mocks) para satisfazer o banco de dados
        Comanda comanda = new Comanda();
        comanda.setMesa(mesa);
        comanda.setStatus(StatusComanda.ABERTA);
        comanda.setDataHoraAbertura(LocalDateTime.now());

        // 🚀 ALTERADO: Tipagem corrigida de String para UUID com segurança
        comanda.setEmpresaId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        comanda.setFilialId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        Comanda comandaSalva = comandaRepository.save(comanda);

        // 5. Cria a Subconta pai obrigatória
        Subconta contaPai = new Subconta();
        contaPai.setComanda(comandaSalva);
        contaPai.setNumeroConta(1);
        contaPai.setPago(false);
        subcontaRepository.save(contaPai);

        return ResponseEntity.ok(comandaSalva);
    }
}