package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ComboProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ComboProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.ComboProduto;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ComboProdutoRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ComboProdutoService {

    @Autowired private ComboProdutoRepository comboProdutoRepository;
    @Autowired private ProdutoRepository produtoRepository;

    @Transactional
    public ComboProdutoResponseDTO associarProdutoAoCombo(ComboProdutoRequestDTO dto) {
        Produto comboPai = produtoRepository.findById(dto.comboId())
                .orElseThrow(() -> new ResourceNotFoundException("Combo pai não localizado."));
        Produto produtoFilho = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto filho não localizado."));

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
}