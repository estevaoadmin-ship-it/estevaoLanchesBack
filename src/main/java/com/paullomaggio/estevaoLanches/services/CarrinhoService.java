package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException; // Importar ResourceNotFoundException
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class CarrinhoService {

    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    public CarrinhoService(CarrinhoRepository carrinhoRepository,
                           ClienteRepository clienteRepository,
                           ProdutoRepository produtoRepository) {
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional
    public CarrinhoResponseDTO adicionarItem(UUID clienteId, ItemCarrinhoRequestDTO dto) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId).orElseGet(() -> {
            Carrinho novoCarrinho = new Carrinho();
            novoCarrinho.setCliente(clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado!"))); // Alterado para ResourceNotFoundException
            return novoCarrinho;
        });

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado!")); // Alterado para ResourceNotFoundException

        // Verifica se o item já existe no carrinho
        Optional<ItemCarrinho> itemExistente = carrinho.getItens().stream()
                .filter(item -> item.getProduto() != null && item.getProduto().getId() != null && item.getProduto().getId().equals(produto.getId()))
                .findFirst();

        if (itemExistente.isPresent()) {
            // Se existe, atualiza a quantidade
            ItemCarrinho item = itemExistente.get();
            item.setQuantidade(item.getQuantidade() + dto.quantidade());
            item.setObservacao(dto.observacao() != null ? dto.observacao() : item.getObservacao());
        } else {
            // Se não existe, adiciona um novo item
            ItemCarrinho novoItem = new ItemCarrinho();
            novoItem.setProduto(produto);
            novoItem.setQuantidade(dto.quantidade());
            novoItem.setObservacao(dto.observacao());
            novoItem.setCarrinho(carrinho);
            carrinho.getItens().add(novoItem);
        }

        Carrinho carrinhoGuardado = carrinhoRepository.save(carrinho);
        return new CarrinhoResponseDTO(carrinhoGuardado);
    }

    @Transactional
    public CarrinhoResponseDTO atualizarQuantidadeItem(UUID clienteId, UUID itemId, Integer quantidade) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado para o cliente: " + clienteId)); // Alterado para ResourceNotFoundException

        ItemCarrinho itemParaAtualizar = carrinho.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item do carrinho não encontrado: " + itemId)); // Alterado para ResourceNotFoundException

        if (quantidade <= 0) {
            carrinho.getItens().remove(itemParaAtualizar);
        } else {
            itemParaAtualizar.setQuantidade(quantidade);
        }

        Carrinho carrinhoAtualizado = carrinhoRepository.save(carrinho);
        return new CarrinhoResponseDTO(carrinhoAtualizado);
    }

    @Transactional
    public CarrinhoResponseDTO removerItem(UUID clienteId, UUID itemId) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado para o cliente: " + clienteId)); // Alterado para ResourceNotFoundException

        ItemCarrinho itemParaRemover = carrinho.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item do carrinho não encontrado: " + itemId)); // Alterado para ResourceNotFoundException

        carrinho.getItens().remove(itemParaRemover);

        Carrinho carrinhoAtualizado = carrinhoRepository.save(carrinho);
        return new CarrinhoResponseDTO(carrinhoAtualizado);
    }

    public CarrinhoResponseDTO buscarCarrinhoPorClienteId(UUID clienteId) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado para o cliente: " + clienteId)); // Alterado para ResourceNotFoundException
        return new CarrinhoResponseDTO(carrinho);
    }
}