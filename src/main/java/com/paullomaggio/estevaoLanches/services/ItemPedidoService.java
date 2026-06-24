package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ItemPedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.enums.StatusEnvioItem;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ItemPedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Camada de serviço responsável por gerenciar o estado atômico de cada lanche lançado.
 */
@Service
public class ItemPedidoService {

    @Autowired
    private ItemPedidoRepository itemPedidoRepository;

    @Transactional(readOnly = true)
    public ItemPedidoResponseDTO buscarPorId(UUID id) {
        ItemPedido item = itemPedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item de pedido não localizado. ID: " + id));
        return new ItemPedidoResponseDTO(item);
    }

    @Transactional(readOnly = true)
    public List<ItemPedidoResponseDTO> listarPorPedido(UUID pedidoId) {
        return itemPedidoRepository.findByPedidoId(pedidoId).stream()
                .map(ItemPedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Altera o status do item para ENVIADO.
     * Chamado de forma automatizada pelo PedidoService após a impressão e commit dos lotes.
     */
    @Transactional
    public void consolidarEnvioDoItem(UUID id) {
        ItemPedido item = itemPedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item de pedido não localizado para consolidação."));
        item.setStatusEnvio(StatusEnvioItem.ENVIADO);
        itemPedidoRepository.save(item);
    }

    /**
     * Consolida em lote todos os itens que estavam em rascunho de uma subconta.
     */
    @Transactional
    public void consolidarEnvioEmLote(UUID pedidoId, Integer numeroConta) {
        List<ItemPedido> itens = itemPedidoRepository.findByPedidoIdAndNumeroConta(pedidoId, numeroConta);
        for (ItemPedido item : itens) {
            if (item.getStatusEnvio() == StatusEnvioItem.AGUARDANDO_ENVIO) {
                item.setStatusEnvio(StatusEnvioItem.ENVIADO);
            }
        }
        itemPedidoRepository.saveAll(itens);
    }
}