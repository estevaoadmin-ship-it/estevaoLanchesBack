package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboCustomizacaoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CarrinhoService {

    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AdicionalRepository adicionalRepository;
    private final ComboProdutoRepository comboProdutoRepository; // Novo
    private final AdicionalValidationService adicionalValidationService; // Novo
    private final ItemCarrinhoComboCustomizacaoRepository itemCarrinhoComboCustomizacaoRepository; // Novo

    public CarrinhoService(CarrinhoRepository carrinhoRepository,
                           ClienteRepository clienteRepository,
                           ProdutoRepository produtoRepository,
                           AdicionalRepository adicionalRepository,
                           ComboProdutoRepository comboProdutoRepository,
                           AdicionalValidationService adicionalValidationService,
                           ItemCarrinhoComboCustomizacaoRepository itemCarrinhoComboCustomizacaoRepository) {
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.adicionalRepository = adicionalRepository;
        this.comboProdutoRepository = comboProdutoRepository;
        this.adicionalValidationService = adicionalValidationService;
        this.itemCarrinhoComboCustomizacaoRepository = itemCarrinhoComboCustomizacaoRepository;
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

        // Carregar e validar adicionais do item principal (se houver)
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

        // Busca por item existente com base em Produto, Adicionais, Observação E CUSTOMIZAÇÕES DE COMBO
        Optional<ItemCarrinho> itemExistente = carrinho.getItens().stream()
                .filter(item -> item.getProduto() != null && item.getProduto().getId().equals(produto.getId()) &&
                        mesmosAdicionais(item.getAdicionais(), adicionais) &&
                        Objects.equals(normalizarObservacao(item.getObservacao()), observacaoNormalizada) &&
                        mesmasCustomizacoesCombo(item.getCustomizacoesCombo(), dto.itensCombo())) // Nova comparação
                .findFirst();

        ItemCarrinho itemCarrinho;
        if (itemExistente.isPresent()) {
            itemCarrinho = itemExistente.get();
            itemCarrinho.setQuantidade(itemCarrinho.getQuantidade() + dto.quantidade());
            // Se o item já existe com a mesma composição, não precisamos atualizar as customizações,
            // pois elas já são parte da identidade.
        } else {
            // Se não existe, adiciona um novo item
            itemCarrinho = new ItemCarrinho();
            itemCarrinho.setProduto(produto);
            itemCarrinho.setQuantidade(dto.quantidade());
            itemCarrinho.setObservacao(dto.observacao());
            itemCarrinho.setCarrinho(carrinho);
            itemCarrinho.getAdicionais().addAll(adicionais); // Associa os adicionais
            carrinho.getItens().add(itemCarrinho);
        }

        // Processar customizações de combo
        if (dto.itensCombo() != null && !dto.itensCombo().isEmpty()) {
            if (!Boolean.TRUE.equals(produto.getIsCombo())) {
                throw new BusinessRuleException("O produto " + produto.getNome() + " não é um combo e não pode ter customizações de combo.");
            }

            // Limpa as customizações existentes para este itemCarrinho, pois o DTO representa o estado atual
            itemCarrinho.getCustomizacoesCombo().clear();

            for (ItemComboCustomizacaoRequestDTO customizacaoDto : dto.itensCombo()) {
                // 1. Localizar ComboProduto
                ComboProduto comboProdutoConfig = comboProdutoRepository.findById(customizacaoDto.comboProdutoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Configuração de ComboProduto não localizada para o ID: " + customizacaoDto.comboProdutoId()));

                // 2. Validar que pertence ao Combo do ItemCarrinho
                if (!comboProdutoConfig.getCombo().getId().equals(produto.getId())) {
                    throw new BusinessRuleException("ComboProduto com ID " + customizacaoDto.comboProdutoId() + " não pertence ao combo principal " + produto.getNome());
                }

                // 3. Obter o produto interno e 4. Reutilizar AdicionalValidationService
                List<Adicional> adicionaisValidados = new ArrayList<>();
                if (customizacaoDto.adicionaisIds() != null && !customizacaoDto.adicionaisIds().isEmpty()) {
                    adicionaisValidados = adicionalValidationService.validarAdicionaisPermitidos(comboProdutoConfig.getProduto().getId(), customizacaoDto.adicionaisIds());
                }

                // 5, 6, 7, 8. Criar e vincular ItemCarrinhoComboCustomizacao
                ItemCarrinhoComboCustomizacao customizacao = new ItemCarrinhoComboCustomizacao();
                customizacao.setItemCarrinho(itemCarrinho);
                customizacao.setComboProdutoId(customizacaoDto.comboProdutoId());
                customizacao.getAdicionais().addAll(adicionaisValidados);
                customizacao.setObservacao(customizacaoDto.observacao()); // Set the observation
                itemCarrinho.getCustomizacoesCombo().add(customizacao);
            }
        } else if (dto.itensCombo() != null && dto.itensCombo().isEmpty()) {
            // Se itensCombo foi explicitamente enviado como lista vazia, limpar as customizações existentes
            itemCarrinho.getCustomizacoesCombo().clear();
        }
        // Se dto.itensCombo() for null, significa que é um request antigo ou sem customização,
        // então as customizações existentes (se houver) são mantidas, ou nenhuma é adicionada.
        // Isso garante a retrocompatibilidade.

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
        // Devido a cascade = CascadeType.ALL e orphanRemoval = true em ItemCarrinho.customizacoesCombo,
        // as customizações associadas a itemParaRemover serão automaticamente removidas.

        Carrinho carrinhoAtualizado = carrinhoRepository.save(carrinho);
        return new CarrinhoResponseDTO(carrinhoAtualizado);
    }

    @Transactional(readOnly = true) // CORREÇÃO 1: Adicionado @Transactional(readOnly = true)
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

    private boolean mesmasCustomizacoesCombo(List<ItemCarrinhoComboCustomizacao> customizacoesAtuais, List<ItemComboCustomizacaoRequestDTO> customizacoesNovas) {
        if (customizacoesAtuais == null || customizacoesAtuais.isEmpty()) {
            return customizacoesNovas == null || customizacoesNovas.isEmpty();
        }
        if (customizacoesNovas == null || customizacoesNovas.isEmpty()) {
            return false; // Se as atuais não são vazias, mas as novas são, não são as mesmas
        }
        if (customizacoesAtuais.size() != customizacoesNovas.size()) {
            return false;
        }

        // Converter customizacoesAtuais para um formato comparável
        Set<String> atuaisSet = customizacoesAtuais.stream()
                .map(c -> c.getComboProdutoId().toString() + ":" +
                        c.getAdicionais().stream()
                                .map(a -> a.getId().toString())
                                .sorted()
                                .collect(Collectors.joining(",")) + ":" +
                        normalizarObservacao(c.getObservacao())) // Incluir observação na comparação
                .collect(Collectors.toSet());

        // Converter customizacoesNovas para um formato comparável
        Set<String> novasSet = customizacoesNovas.stream()
                .map(c -> c.comboProdutoId().toString() + ":" +
                        // CORREÇÃO 2: Tornar adicionaisIds null-safe
                        (c.adicionaisIds() == null || c.adicionaisIds().isEmpty()
                                ? ""
                                : c.adicionaisIds().stream()
                                        .map(UUID::toString)
                                        .sorted()
                                        .collect(Collectors.joining(","))) + ":" +
                        normalizarObservacao(c.observacao())) // Incluir observação na comparação
                .collect(Collectors.toSet());

        return atuaisSet.equals(novasSet);
    }
}