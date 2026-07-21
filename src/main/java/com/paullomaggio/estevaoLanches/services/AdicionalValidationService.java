package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdicionalValidationService {

    private final ProdutoRepository produtoRepository;
    private final AdicionalRepository adicionalRepository;

    public List<Adicional> validarAdicionaisPermitidos(UUID produtoId, List<UUID> adicionaisIds) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não localizado para validação de adicionais."));

        List<Adicional> adicionaisSolicitados = adicionalRepository.findAllById(adicionaisIds);

        if (adicionaisSolicitados.size() != adicionaisIds.size()) {
            throw new BusinessRuleException("Um ou mais IDs de adicionais fornecidos são inválidos ou não existem.");
        }

        Set<UUID> adicionaisPermitidosIds = produto.getAdicionais().stream()
                .map(Adicional::getId)
                .collect(Collectors.toSet());

        for (Adicional adicional : adicionaisSolicitados) {
            if (!adicionaisPermitidosIds.contains(adicional.getId())) {
                throw new BusinessRuleException("Adicional '" + adicional.getNome() + "' não é permitido para o produto '" + produto.getNome() + "'.");
            }
        }
        return adicionaisSolicitados;
    }
}
