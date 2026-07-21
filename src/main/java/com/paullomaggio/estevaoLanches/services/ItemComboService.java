package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ItemComboRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemComboRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemPedidoRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ItemComboService {

    private final ItemComboRepository itemComboRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final AdicionalRepository adicionalRepository; // Still needed for other methods if any
    private final ProdutoRepository produtoRepository; // Still needed for other methods if any
    private final PedidoService pedidoService; // Injetar PedidoService para recalcular o total
    private final AdicionalValidationService adicionalValidationService; // Injetar o novo serviço

    public ItemComboService(
            ItemComboRepository itemComboRepository,
            ItemPedidoRepository itemPedidoRepository,
            AdicionalRepository adicionalRepository,
            ProdutoRepository produtoRepository,
            PedidoService pedidoService,
            AdicionalValidationService adicionalValidationService) {
        this.itemComboRepository = itemComboRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.adicionalRepository = adicionalRepository;
        this.produtoRepository = produtoRepository;
        this.pedidoService = pedidoService;
        this.adicionalValidationService = adicionalValidationService;
    }

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

    @Transactional
    public PedidoResponseDTO atualizarAdicionaisDoItemCombo(UUID itemComboId, List<UUID> adicionaisIds) {
        ItemCombo itemCombo = itemComboRepository.findById(itemComboId)
                .orElseThrow(() -> new ResourceNotFoundException("Item de combo não localizado."));

        // Usar o serviço de validação compartilhado
        List<Adicional> adicionaisValidados = adicionalValidationService.validarAdicionaisPermitidos(itemCombo.getProdutoId(), adicionaisIds);

        // Substituir a lista atual de adicionais do ItemCombo
        itemCombo.setAdicionais(adicionaisValidados);

        // Persistir a alteração
        itemComboRepository.save(itemCombo);

        // Recalcular Pedido.total corretamente e retornar o PedidoResponseDTO atualizado
        // Delegar para PedidoService para recalcular o total do pedido pai
        return pedidoService.recalcularTotalPedido(itemCombo.getItemPedido().getPedido().getId());
    }
}