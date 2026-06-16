package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoStatusRequestDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AdicionalRepository adicionalRepository;

    @Transactional
    public PedidoResponseDTO finalizarPedido(CheckoutRequestDTO dto) {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("O estabelecimento esta fechado. Abra o caixa para iniciar as vendas!");
        }

        Pedido pedido = new Pedido();
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setTipo(dto.tipo());
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setNumeroMesa(dto.numeroMesa());
        pedido.setObservacaoGeral(dto.observacaoGeral());
        pedido.setDataHora(LocalDateTime.now());

        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setValorRecebido(dto.valorRecebido());
        pedido.setNomeClienteBalcao(dto.nomeClienteBalcao());

        if (pedido.getItens() == null) {
            pedido.setItens(new ArrayList<>());
        }

        BigDecimal totalPedido = BigDecimal.ZERO;

        if (dto.itens() != null && !dto.itens().isEmpty()) {

            if (dto.clienteId() != null) {
                Cliente cliente = clienteRepository.findById(dto.clienteId())
                        .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado!"));
                pedido.setCliente(cliente);
            } else {
                pedido.setCliente(null);
            }

            for (var itemDto : dto.itens()) {
                Produto produto = produtoRepository.findById(itemDto.produtoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + itemDto.produtoId()));

                ItemPedido itemPedido = new ItemPedido();
                itemPedido.setPedido(pedido);
                itemPedido.setProduto(produto);
                itemPedido.setQuantidade(itemDto.quantidade());
                itemPedido.setObservacaoItem(itemDto.observacao());
                itemPedido.setPrecoUnitario(produto.getPreco());

                if (itemDto.adicionaisIds() != null && !itemDto.adicionaisIds().isEmpty()) {
                    List<Adicional> adicionais = adicionalRepository.findAllById(itemDto.adicionaisIds());
                    itemPedido.setAdicionais(adicionais);
                }

                BigDecimal precoAdicionais = itemPedido.getAdicionais().stream()
                        .map(Adicional::getPreco)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal subtotal = itemPedido.getPrecoUnitario().add(precoAdicionais)
                        .multiply(BigDecimal.valueOf(itemPedido.getQuantidade()));

                totalPedido = totalPedido.add(subtotal);
                pedido.getItens().add(itemPedido);
            }
        } else {
            if (dto.clienteId() == null) {
                throw new BusinessRuleException("Para recuperar o carrinho do banco, o clienteId e obrigatorio!");
            }

            Carrinho carrinho = carrinhoRepository.findByClienteId(dto.clienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Carrinho nao encontrado!"));

            if (carrinho.getItens().isEmpty()) {
                throw new BusinessRuleException("O carrinho esta vazio!");
            }

            pedido.setCliente(carrinho.getCliente());

            for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
                ItemPedido itemPedido = new ItemPedido();
                itemPedido.setPedido(pedido);
                itemPedido.setProduto(itemCarrinho.getProduto());
                itemPedido.setQuantidade(itemCarrinho.getQuantidade());
                itemPedido.setObservacaoItem(itemCarrinho.getObservacao());
                itemPedido.setPrecoUnitario(itemCarrinho.getProduto().getPreco());

                BigDecimal subtotal = itemPedido.getPrecoUnitario().multiply(BigDecimal.valueOf(itemPedido.getQuantidade()));
                totalPedido = totalPedido.add(subtotal);
                pedido.getItens().add(itemPedido);
            }

            carrinho.getItens().clear();
            carrinhoRepository.save(carrinho);
        }

        pedido.setTotal(totalPedido);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));
        return new PedidoResponseDTO(pedido);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoRepository.findAll().stream()
                .map(PedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoCliente(UUID clienteId) {
        return pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId).stream()
                .map(PedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPedidosAtivosMonitor() {
        List<StatusPedido> ativos = Arrays.asList(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO, StatusPedido.PRONTO, StatusPedido.EM_ROTA);
        return pedidoRepository.findByStatusInOrderByDataHoraAsc(ativos).stream()
                .map(PedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public PedidoResponseDTO atualizarStatus(UUID id, PedidoStatusRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new BusinessRuleException("Nao e possivel alterar o status de um pedido Finalizado ou Cancelado.");
        }

        pedido.setStatus(dto.status());
        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO cancelarPedido(UUID id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO) {
            throw new BusinessRuleException("Pedidos ja entregues (Finalizados) nao podem ser cancelados.");
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO adicionarItemPedido(UUID pedidoId, ItemPedidoRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.EM_ROTA) {
            throw new BusinessRuleException("Nao e possivel adicionar itens a um pedido que ja esta em rota, finalizado ou cancelado.");
        }

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado no cardapio."));

        ItemPedido novoItem = new ItemPedido();
        novoItem.setPedido(pedido);
        novoItem.setProduto(produto);
        novoItem.setQuantidade(dto.quantidade());
        novoItem.setPrecoUnitario(produto.getPreco());
        novoItem.setObservacaoItem(dto.observacao());

        if (dto.adicionaisIds() != null && !dto.adicionaisIds().isEmpty()) {
            List<Adicional> adicionais = adicionalRepository.findAllById(dto.adicionaisIds());
            novoItem.setAdicionais(adicionais);
        }

        BigDecimal precoAdicionais = novoItem.getAdicionais().stream()
                .map(Adicional::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        pedido.getItens().add(novoItem);

        BigDecimal valorAdicional = produto.getPreco().add(precoAdicionais)
                .multiply(BigDecimal.valueOf(dto.quantidade()));

        pedido.setTotal(pedido.getTotal().add(valorAdicional));

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO removerItemPedido(UUID pedidoId, UUID itemId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.EM_ROTA) {
            throw new BusinessRuleException("Nao e possivel alterar os itens de um pedido que ja esta em rota, finalizado ou cancelado.");
        }

        ItemPedido itemParaRemover = pedido.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item nao encontrado nesta comanda."));

        BigDecimal precoAdicionais = itemParaRemover.getAdicionais().stream()
                .map(Adicional::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorSubtrair = itemParaRemover.getPrecoUnitario().add(precoAdicionais)
                .multiply(BigDecimal.valueOf(itemParaRemover.getQuantidade()));

        pedido.setTotal(pedido.getTotal().subtract(valorSubtrair));
        pedido.getItens().remove(itemParaRemover);

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO atualizarAdicionaisDoItem(UUID pedidoId, UUID itemId, List<UUID> adicionaisIds) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));

        ItemPedido item = pedido.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item nao encontrado nesta comanda."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.EM_ROTA) {
            throw new BusinessRuleException("Nao e possivel alterar os adicionais de um pedido que ja esta em rota, finalizado ou cancelado.");
        }

        BigDecimal precoAdicionaisAntigos = item.getAdicionais().stream()
                .map(Adicional::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotalAntigo = item.getPrecoUnitario().add(precoAdicionaisAntigos).multiply(BigDecimal.valueOf(item.getQuantidade()));
        pedido.setTotal(pedido.getTotal().subtract(subtotalAntigo));

        List<Adicional> novosAdicionais = adicionalRepository.findAllById(adicionaisIds);
        item.setAdicionais(novosAdicionais);

        BigDecimal precoNovosAdicionais = novosAdicionais.stream()
                .map(Adicional::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotalNovo = item.getPrecoUnitario().add(precoNovosAdicionais).multiply(BigDecimal.valueOf(item.getQuantidade()));
        pedido.setTotal(pedido.getTotal().add(subtotalNovo));

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public void excluirFisicamente(UUID id) {
        throw new BusinessRuleException("Por razoes financeiras e de auditoria, pedidos nao podem ser excluidos do banco de dados. Utilize a funcao de Cancelamento.");
    }
}