package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao.StatusImpressao;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.FilaImpressaoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class FilaImpressaoService {

    private final FilaImpressaoRepository repository;

    public FilaImpressaoService(FilaImpressaoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FilaImpressao> buscarPendentes() {
        return repository.findByStatus(StatusImpressao.PENDENTE);
    }

    @Transactional
    public void marcarComoImpresso(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("O ID da fila de impressao nao pode ser nulo.");
        }

        FilaImpressao item = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Item de impressao nao encontrado."));

        if (item.getStatus() == StatusImpressao.IMPRESSO) {
            throw new BusinessRuleException("Transicao invalida: O item ja consta como IMPRESSO.");
        }
        if (item.getStatus() != StatusImpressao.PROCESSANDO) {
            throw new BusinessRuleException("Transicao invalida: O item deve estar em PROCESSANDO para ser concluido. Status atual: " + item.getStatus());
        }

        item.setStatus(StatusImpressao.IMPRESSO);
        item.setImpressoEm(LocalDateTime.now());
        repository.saveAndFlush(item);
    }

    @Transactional
    public void alterarParaProcessando(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("O ID da fila de impressao nao pode ser nulo.");
        }

        FilaImpressao item = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Item de impressao nao encontrado."));

        if (item.getStatus() == StatusImpressao.IMPRESSO) {
            throw new BusinessRuleException("Nao e possivel reprocessar um item ja impresso.");
        }

        item.setStatus(StatusImpressao.PROCESSANDO);
        item.setUltimaTentativa(LocalDateTime.now());
        repository.saveAndFlush(item);
    }

    @Transactional
    public void reverterParaPendente(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("O ID da fila de impressao nao pode ser nulo.");
        }

        FilaImpressao item = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Item de impressao nao encontrado."));

        if (item.getStatus() == StatusImpressao.IMPRESSO) {
            throw new BusinessRuleException("Nao e possivel reverter um item que ja foi impresso.");
        }

        item.setStatus(StatusImpressao.PENDENTE);
        item.setLogErro("Bridge: Falha fisica no hardware de impressao. Status revertido para nova tentativa.");
        repository.saveAndFlush(item);
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void verificarProcessamentosTravados() {
        List<FilaImpressao> travados = repository.findByStatus(StatusImpressao.PROCESSANDO);
        LocalDateTime limite = LocalDateTime.now().minusMinutes(10);

        for (FilaImpressao item : travados) {
            try {
                if (item.getUltimaTentativa() != null && item.getUltimaTentativa().isBefore(limite)) {
                    item.setStatus(StatusImpressao.PENDENTE);
                    item.setTentativas(item.getTentativas() + 1);
                    item.setUltimaTentativa(LocalDateTime.now());
                    item.setLogErro("Watchdog: Tempo limite esgotado em PROCESSANDO.");
                    repository.saveAndFlush(item);
                }
            } catch (Exception e) {
                item.setLogErro("Falha catastrofica no Watchdog: " + e.getMessage());
            }
        }
    }
}