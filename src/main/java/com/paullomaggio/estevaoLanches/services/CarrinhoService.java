package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CarrinhoService {

    @Autowired
    private CarrinhoRepository carrinhoRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional
    public CarrinhoResponseDTO adicionarItem(UUID clienteId, ItemCarrinhoRequestDTO dto) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(clienteId).orElseGet(() -> {
            Carrinho novoCarrinho = new Carrinho();
            novoCarrinho.setCliente(clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente não encontrado!")));
            return novoCarrinho;
        });

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado!"));

        ItemCarrinho novoItem = new ItemCarrinho();
        novoItem.setProduto(produto);
        novoItem.setQuantidade(dto.quantidade());
        novoItem.setObservacao(dto.observacao());
        novoItem.setCarrinho(carrinho);

        carrinho.getItens().add(novoItem);
        Carrinho carrinhoGuardado = carrinhoRepository.save(carrinho);

        return new CarrinhoResponseDTO(carrinhoGuardado);
    }
}