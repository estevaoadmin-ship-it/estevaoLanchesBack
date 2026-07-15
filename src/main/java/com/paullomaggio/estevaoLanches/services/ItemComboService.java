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
    private final AdicionalRepository adicionalRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoService pedidoService; // Injetar PedidoService para recalcular o total
    // private final ComboProdutoRepository comboProdutoRepository; // Injeção de ComboProdutoRepository - REMOVIDO

    // Injeção de dependência via construtor
    public ItemComboService(
            ItemComboRepository itemComboRepository,
            ItemPedidoRepository itemPedidoRepository,
            AdicionalRepository adicionalRepository,
            ProdutoRepository produtoRepository,
            PedidoService pedidoService) { // ComboProdutoRepository REMOVIDO do construtor
        this.itemComboRepository = itemComboRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.adicionalRepository = adicionalRepository;
        this.produtoRepository = produtoRepository;
        this.pedidoService = pedidoService;
        // this.comboProdutoRepository = comboProdutoRepository; // Inicialização - REMOVIDO
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

        // 1. Localizar o Produto correspondente a ItemCombo.produtoId
        Produto produtoInterno = produtoRepository.findById(itemCombo.getProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto interno do combo não localizado."));

        // 2. Carregar os adicionais enviados
        List<Adicional> adicionaisSolicitados = adicionalRepository.findAllById(adicionaisIds);

        // 3. Validar que TODOS os adicionais enviados são permitidos para aquele Produto
        // E que todos os IDs enviados correspondem a adicionais existentes
        if (adicionaisSolicitados.size() != adicionaisIds.size()) {
            // Some requested IDs did not map to existing Adicional entities
            throw new BusinessRuleException("Um ou mais IDs de adicionais fornecidos são inválidos.");
        }

        Set<UUID> adicionaisPermitidosIds = produtoInterno.getAdicionais().stream()
                .map(Adicional::getId)
                .collect(Collectors.toSet());

        for (Adicional adicional : adicionaisSolicitados) {
            if (!adicionaisPermitidosIds.contains(adicional.getId())) {
                throw new BusinessRuleException("Adicional '" + adicional.getNome() + "' não é permitido para o produto '" + produtoInterno.getNome() + "'.");
            }
        }

        // 4. Substituir a lista atual de adicionais do ItemCombo
        itemCombo.setAdicionais(adicionaisSolicitados);

        // 5. Persistir a alteração
        itemComboRepository.save(itemCombo);

        // 6. Recalcular Pedido.total corretamente e retornar o PedidoResponseDTO atualizado
        // Delegar para PedidoService para recalcular o total do pedido pai
        return pedidoService.recalcularTotalPedido(itemCombo.getItemPedido().getPedido().getId());
    }

    // NOVO MÉTODO: Criar snapshots de ItemCombo para um ItemPedido individual - REMOVIDO
    // public void criarSnapshotsDoCombo(ItemPedido itemPedido) {
    //     if (itemPedido == null
    //             || itemPedido.getProduto() == null
    //             || itemPedido.getId() == null) {
    //         return;
    //     }
    //
    //     // Verifica se o produto do ItemPedido é um combo
    //     if (!Boolean.TRUE.equals(itemPedido.getProduto().getIsCombo())) {
    //         return;
    //     }
    //
    //     // Proteção contra duplicidade: verifica se já existem ItemCombos para este ItemPedido
    //     List<ItemCombo> existentes = itemComboRepository.findByItemPedidoId(itemPedido.getId());
    //     if (!existentes.isEmpty()) {
    //         return;
    //     }
    //
    //     // Busca a composição do combo
    //     List<ComboProduto> composicao = comboProdutoRepository.findByComboId(itemPedido.getProduto().getId());
    //
    //     if (composicao.isEmpty()) {
    //         return; // Combo sem composição, apenas retorna
    //     }
    //
    //     // Cria os snapshots de ItemCombo
    //     List<ItemCombo> snapshots = composicao.stream()
    //             .map(config -> {
    //                 Produto produtoInterno = config.getProduto();
    //                 ItemCombo itemCombo = new ItemCombo();
    //
    //                 itemCombo.setItemPedido(itemPedido);
    //                 itemCombo.setProdutoId(produtoInterno.getId());
    //                 itemCombo.setNomeProduto(produtoInterno.getNome());
    //                 itemCombo.setQuantidade(config.getQuantidade()); // Usa a quantidade da composição do combo
    //                 itemCombo.setPrecoUnitario(produtoInterno.getPreco());
    //
    //                 return itemCombo;
    //             })
    //             .collect(Collectors.toList());
    //
    //     // Persiste todos os snapshots de uma vez
    //     itemComboRepository.saveAll(snapshots);
    // }

    // NOVO MÉTODO: Processar todos os ItemPedidos de um Pedido para criar snapshots de combos - REMOVIDO
    // public void criarSnapshotsDosCombos(Pedido pedido) {
    //     if (pedido == null || pedido.getItens() == null) {
    //         return;
    //     }
    //
    //     pedido.getItens().forEach(this::criarSnapshotsDoCombo);
    // }
}