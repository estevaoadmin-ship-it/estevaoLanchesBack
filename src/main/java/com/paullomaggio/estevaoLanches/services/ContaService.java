package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ContaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ContaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContaService {

    private final ContaRepository contaRepository;
    private final ComandaRepository comandaRepository;

    // Injeção de dependência via construtor
    public ContaService(ContaRepository contaRepository, ComandaRepository comandaRepository) {
        this.contaRepository = contaRepository;
        this.comandaRepository = comandaRepository;
    }

    /**
     * 🎯 REAJUSTADO: Cria uma subconta atrelada à Comanda Mestre e inicializa seu respectivo Cliente 1:1.
     */
    @Transactional
    public ContaResponseDTO criar(ContaRequestDTO dto) {
        Comanda comanda = comandaRepository.findById(dto.comandaId())
                .orElseThrow(() -> new ResourceNotFoundException("Comanda mestre não localizada. ID: " + dto.comandaId()));

        contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), dto.numeroConta())
                .ifPresent(c -> {
                    throw new BusinessRuleException("Esta mesa já possui a subconta " + dto.numeroConta() + " ativa.");
                });

        Conta conta = new Conta();
        conta.setNumeroConta(dto.numeroConta());
        conta.setComanda(comanda);
        conta.setPago(false);
        conta.setValorTotal(BigDecimal.ZERO);

        return new ContaResponseDTO(contaRepository.save(conta));
    }

    @Transactional(readOnly = true)
    public ContaResponseDTO buscarPorId(UUID id) {
        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada com o ID informado."));
        return new ContaResponseDTO(conta);
    }

    @Transactional(readOnly = true)
    public List<ContaResponseDTO> listarPorComanda(UUID comandaId) {
        if (!comandaRepository.existsById(comandaId)) {
            throw new ResourceNotFoundException("Comanda mestre não localizada.");
        }
        return contaRepository.findByComandaId(comandaId).stream()
                .map(ContaResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletar(UUID id) {
        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada para exclusão."));

        if (Boolean.FALSE.equals(conta.getPago()) && conta.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("Não é possível excluir uma conta com saldo devedor pendente.");
        }
        contaRepository.delete(conta);
    }
}