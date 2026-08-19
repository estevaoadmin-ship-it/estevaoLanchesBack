package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ComboProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ComboProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ComboComposicaoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComposicaoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.ComboProduto;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ComboProdutoRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ComboProdutoService {

    private final ComboProdutoRepository comboProdutoRepository;
    private final ProdutoRepository produtoRepository;

    // Injeção de dependência via construtor
    public ComboProdutoService(ComboProdutoRepository comboProdutoRepository, ProdutoRepository produtoRepository) {
        this.comboProdutoRepository = comboProdutoRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional
    public ComboProdutoResponseDTO associarProdutoAoCombo(ComboProdutoRequestDTO dto) {
        Produto comboPai = produtoRepository.findById(dto.comboId())
                .orElseThrow(() -> new ResourceNotFoundException("Combo pai não localizado."));
        Produto produtoFilho = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto filho não localizado."));

        // 🛡️ BLINDAGEM: Impede a criação de vínculo duplicado (1 Combo + 1 Produto → no máximo 1 ComboProduto)
        boolean vinculoJaExiste = comboProdutoRepository.findByComboId(dto.comboId()).stream()
                .anyMatch(cp -> cp.getProduto().getId().equals(dto.produtoId()));
        if (vinculoJaExiste) {
            throw new BusinessRuleException("Este produto já está associado a este combo.");
        }

        ComboProduto CP = new ComboProduto();
        CP.setCombo(comboPai);
        CP.setProduto(produtoFilho);
        CP.setQuantidade(dto.quantidade());

        return new ComboProdutoResponseDTO(comboProdutoRepository.save(CP));
    }

    @Transactional(readOnly = true)
    public List<ComboProdutoResponseDTO> listarEstruturaDoCombo(UUID comboId) {
        return comboProdutoRepository.findByComboId(comboId).stream()
                .map(ComboProdutoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void desassociarItem(UUID id) {
        if (!comboProdutoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Vínculo de combo não encontrado.");
        }
        comboProdutoRepository.deleteById(id);
    }
@Transactional
    public void atualizarComposicaoDoCombo(UUID comboId, List<ItemComposicaoRequestDTO> novosItens) {
        // Validate combo exists
        Produto combo = produtoRepository.findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("Combo não encontrado."));

        // 🛡️ BLINDAGEM: Rejeita produto duplicado no request ANTES de qualquer alteração persistente.
        // Garante a invariante: 1 Combo + 1 Produto → no máximo 1 vínculo ComboProduto.
        Set<UUID> produtosNoRequest = new HashSet<>();
        for (ItemComposicaoRequestDTO item : novosItens) {
            if (!produtosNoRequest.add(item.produtoId())) {
                throw new BusinessRuleException("Produto duplicado na composição: " + item.produtoId());
            }
        }

        // Get current associations
        List<ComboProduto> associacoesAtuais = comboProdutoRepository.findByComboId(comboId);

        // Map productId -> ComboProduto for current associations
        Map<UUID, ComboProduto> mapaAtual = new HashMap<>();
        for (ComboProduto cp : associacoesAtuais) {
            mapaAtual.put(cp.getProduto().getId(), cp);
        }

        List<ComboProduto> paraManter = new ArrayList<>();
        List<ComboProduto> paraCriar = new ArrayList<>();

        for (ItemComposicaoRequestDTO item : novosItens) {
            UUID produtoId = item.produtoId();
            Integer quantidade = item.quantidade();

            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + produtoId));

            ComboProduto existente = mapaAtual.get(produtoId);
            if (existente != null) {
                // Update quantity if different
                if (!Objects.equals(existente.getQuantidade(), quantidade)) {
                    existente.setQuantidade(quantidade);
                }
                paraManter.add(existente);
                mapaAtual.remove(produtoId);
            } else {
                ComboProduto novo = new ComboProduto();
                novo.setCombo(combo);
                novo.setProduto(produto);
                novo.setQuantidade(quantidade);
                paraCriar.add(novo);
            }
        }

        // Remaining items in mapaAtual are to be removed
        List<ComboProduto> paraRemover = new ArrayList<>(mapaAtual.values());

        if (!paraRemover.isEmpty()) {
            comboProdutoRepository.deleteAll(paraRemover);
        }
        if (!paraCriar.isEmpty()) {
            comboProdutoRepository.saveAll(paraCriar);
        }
        if (!paraManter.isEmpty()) {
            comboProdutoRepository.saveAll(paraManter);
        }
    }
}