package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class CarrinhoService {

    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AdicionalRepository adicionalRepository; // Injetado AdicionalRepository

    public CarrinhoService(CarrinhoRepository carrinhoRepository,
                           ClienteRepository clienteRepository,
                           ProdutoRepository produtoRepository,
                           AdicionalRepository adicionalRepository) { // Adicionado AdicionalRepository ao construtor
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.adicionalRepository = adicionalRepository; // Inicializado
    }

    @Transactional
    public CarrinhoResponseDTO adicionarItem(UUID clienteId, ItemCarrinhoRequestDTO dto) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId).orElseGet(() -> {
            Carrinho novoCarrinho = new Carrinho();
            novoCarrinho.setCliente(clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado!")));
            return novoCarrinho;
        });

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado!"));

        // Carregar e validar adicionais
        Set<UUID> adicionaisIds = dto.adicionaisIds() == null ? Set.of() : dto.adicionaisIds();
        Set<Adicional> adicionais = new HashSet<>();

        if (!adicionaisIds.isEmpty()) {
            List<Adicional> foundAdicionais = adicionalRepository.findAllById(adicionaisIds);
            if (foundAdicionais.size() != adicionaisIds.size()) {
                Set<UUID> foundIds = foundAdicionais.stream().map(Adicional::getId).collect(Collectors.toSet());
                Set<UUID> missingIds = adicionaisIds.stream()
                        .filter(id -> !foundIds.contains(id))
                        .collect(Collectors.toSet());
                throw new ResourceNotFoundException("Adicionais não encontrados: " + missingIds);
            }
            adicionais.addAll(foundAdicionais);
        }

        // Normalizar observação
        String observacaoNormalizada = normalizarObservacao(dto.observacao());

        // Busca por item existente com base em Produto, Adicionais e Observação
        Optional<ItemCarrinho> itemExistente = carrinho.getItens().stream()
                .filter(item -> item.getProduto() != null && item.getProduto().getId().equals(produto.getId()) &&
                        mesmosAdicionais(item.getAdicionais(), adicionais) &&
                        Objects.equals(normalizarObservacao(item.getObservacao()), observacaoNormalizada))
                .findFirst();

        if (itemExistente.isPresent()) {
            // Se existe, atualiza a quantidade
            ItemCarrinho item = itemExistente.get();
            item.setQuantidade(item.getQuantidade() + dto.quantidade());
            // Observação já é parte da identidade, não deve ser alterada se o item é o mesmo.
            // A linha abaixo foi removida pois a observação não deve ser atualizada se o item já existe com a mesma composição.
            // item.setObservacao(dto.observacao() != null ? dto.observacao() : item.getObservacao());
        } else {
            // Se não existe, adiciona um novo item
            ItemCarrinho novoItem = new ItemCarrinho();
            novoItem.setProduto(produto);
            novoItem.setQuantidade(dto.quantidade());
            novoItem.setObservacao(dto.observacao());
            novoItem.setCarrinho(carrinho);
            novoItem.getAdicionais().addAll(adicionais); // Associa os adicionais
            carrinho.getItens().add(novoItem);
        }

        Carrinho carrinhoGuardado = carrinhoRepository.save(carrinho);
        return new CarrinhoResponseDTO(carrinhoGuardado);
    }

    @Transactional
    public CarrinhoResponseDTO atualizarQuantidadeItem(UUID clienteId, UUID itemId, Integer quantidade) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado para o cliente: " + clienteId));

        ItemCarrinho itemParaAtualizar = carrinho.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item do carrinho não encontrado: " + itemId));

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
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado para o cliente: " + clienteId));

        ItemCarrinho itemParaRemover = carrinho.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item do carrinho não encontrado: " + itemId));

        carrinho.getItens().remove(itemParaRemover);

        Carrinho carrinhoAtualizado = carrinhoRepository.save(carrinho);
        return new CarrinhoResponseDTO(carrinhoAtualizado);
    }

    public CarrinhoResponseDTO buscarCarrinhoPorClienteId(UUID clienteId) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado para o cliente: " + clienteId));
        return new CarrinhoResponseDTO(carrinho);
    }

    // Métodos auxiliares para comparação de identidade
    private boolean mesmosAdicionais(Set<Adicional> atuais, Set<Adicional> novos) {
        Set<UUID> idsAtuais = atuais == null
                ? Set.of()
                : atuais.stream()
                .map(Adicional::getId)
                .collect(Collectors.toSet());

        Set<UUID> idsNovos = novos == null
                ? Set.of()
                : novos.stream()
                .map(Adicional::getId)
                .collect(Collectors.toSet());

        return idsAtuais.equals(idsNovos);
    }

    private String normalizarObservacao(String observacao) {
        if (observacao == null) {
            return "";
        }
        return observacao.trim();
    }
}