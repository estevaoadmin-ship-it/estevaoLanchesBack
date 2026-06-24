package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ItemComboRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboResponseDTO;
import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ItemComboRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemPedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ItemComboService {

    @Autowired private ItemComboRepository itemComboRepository;
    @Autowired private ItemPedidoRepository itemPedidoRepository;

    @Transactional
    public ItemComboResponseDTO lancarItemNoCombo(ItemComboRequestDTO dto) {
        ItemPedido itemPedido = itemPedidoRepository.findById(dto.itemPedidoId())
                .orElseThrow(() -> new ResourceNotFoundException("Item de pedido de destino não localizado."));

        ItemCombo IC = new ItemCombo();
        IC.setItemPedido(itemPedido);
        IC.setProdutoId(dto.produtoId());
        IC.setNomeProduto(dto.nomeProduto());
        IC.setQuantidade(dto.quantidade());
        IC.setPrecoUnitario(dto.precoUnitario());

        return new ItemComboResponseDTO(itemComboRepository.save(IC));
    }

    @Transactional(readOnly = true)
    public List<ItemComboResponseDTO> listarItensDoComboPedido(UUID itemPedidoId) {
        return itemComboRepository.findByItemPedidoId(itemPedidoId).stream()
                .map(ItemComboResponseDTO::new)
                .collect(Collectors.toList());
    }
}