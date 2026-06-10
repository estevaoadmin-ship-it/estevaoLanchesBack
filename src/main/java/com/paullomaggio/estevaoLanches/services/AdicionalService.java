package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.AdicionalRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.AdicionalResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdicionalService {

    @Autowired
    private AdicionalRepository adicionalRepository;

    public List<AdicionalResponseDTO> listarTodos() {
        return adicionalRepository.findAll().stream()
                .map(AdicionalResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdicionalResponseDTO salvar(AdicionalRequestDTO dto) {
        Adicional adicional = new Adicional();
        adicional.setNome(dto.nome());
        adicional.setPreco(dto.preco());

        Adicional adicionalSalvo = adicionalRepository.save(adicional);

        return new AdicionalResponseDTO(adicionalSalvo);
    }
}