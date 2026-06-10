package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdicionalService {

    @Autowired
    private AdicionalRepository adicionalRepository;

    @Transactional(readOnly = true)
    public List<Adicional> listarTodos() {
        return adicionalRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Adicional buscarPorId(UUID id) {
        return adicionalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Adicional não encontrado com o ID: " + id));
    }

    @Transactional
    public Adicional salvar(Adicional adicional) {
        // Regra de negócio simples: impede adicionais com preço negativo
        if (adicional.getPreco() == null || adicional.getPreco().doubleValue() < 0) {
            throw new IllegalArgumentException("O preço do adicional não pode ser negativo ou nulo.");
        }
        return adicionalRepository.save(adicional);
    }

    @Transactional
    public void deletar(UUID id) {
        Adicional adicional = buscarPorId(id);
        adicionalRepository.delete(adicional);
    }
}