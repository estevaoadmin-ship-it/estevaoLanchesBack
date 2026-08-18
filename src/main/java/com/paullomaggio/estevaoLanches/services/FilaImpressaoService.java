package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.FilaImpressaoDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao.StatusImpressao;
import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.FilaImpressaoRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemComboRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FilaImpressaoService {

    private final FilaImpressaoRepository repository;
    private final ItemComboRepository itemComboRepository;

    public FilaImpressaoService(FilaImpressaoRepository repository, ItemComboRepository itemComboRepository) {
        this.repository = repository;
        this.itemComboRepository = itemComboRepository;
    }

    @Transactional(readOnly = true)
    public List<FilaImpressaoDTO> buscarPendentes() {
        // Busca os itens pendentes com pedido e itens já fetchados devido ao JOIN FETCH no repository
        List<FilaImpressao> filaImpressaoList = repository.findByStatus(StatusImpressao.PENDENTE);
        
        if (filaImpressaoList.isEmpty()) {
            return List.of();
        }
        
        // Coleta todos os IDs de ItemPedido de todos os itens de todos os pedidos
        List<UUID> itemPedidoIds = filaImpressaoList.stream()
                .flatMap(fila -> fila.getPedido().getItens().stream())
                .map(ItemPedido::getId)
                .distinct()
                .collect(Collectors.toList());
        
        // Busca todos os ItemCombo para esses IDs em uma única query (evita N+1)
        Map<UUID, List<ItemCombo>> itemComboPorItemPedidoId;
        if (!itemPedidoIds.isEmpty()) {
            List<ItemCombo> todosItemCombo = itemComboRepository.findByItemPedidoIdIn(itemPedidoIds);
            itemComboPorItemPedidoId = todosItemCombo.stream()
                    .collect(Collectors.groupingBy(itemCombo -> itemCombo.getItemPedido().getId()));
        } else {
            itemComboPorItemPedidoId = Map.of();
        }
        
        // Converte cada FilaImpressao para FilaImpressaoDTO com dados enriquecidos
        return filaImpressaoList.stream()
                .map(filaImpressao -> {
                    // Enriquece os ItemPedido do pedido com seus ItemCombo
                    List<ItemPedidoResponseDTO> itensEnriquecidos = filaImpressao.getPedido().getItens().stream()
                            .map(itemPedido -> {
                                List<ItemCombo> itemCombos = itemComboPorItemPedidoId.getOrDefault(itemPedido.getId(), List.of());
                                List<ItemComboResponseDTO> itemComboResponseDTOs = itemCombos.stream()
                                        .map(ItemComboResponseDTO::new)
                                        .collect(Collectors.toList());
                                return new ItemPedidoResponseDTO(itemPedido, itemComboResponseDTOs);
                            })
                            .collect(Collectors.toList());
                    
                    // Cria o PedidoResponseDTO com os itens enriquecidos
                    PedidoResponseDTO pedidoEnriquecido = new PedidoResponseDTO(
                            filaImpressao.getPedido(),
                            itensEnriquecidos
                    );
                    
                    // Cria e retorna o DTO da fila de impressão
                    return new FilaImpressaoDTO(filaImpressao, pedidoEnriquecido);
                })
                .collect(Collectors.toList());
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