package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.*;
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

    @Autowired private CarrinhoRepository carrinhoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private CaixaRepository caixaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private AdicionalRepository adicionalRepository;
    @Autowired private FilaImpressaoRepository filaImpressaoRepository;

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

        pedido.setStatusFinanceiro(dto.formaPagamento() != null ? StatusFinanceiro.PAGO : StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setItens(new ArrayList<>());

        BigDecimal totalPedido = processarItens(pedido, dto);
        pedido.setTotal(totalPedido);

        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        adicionarNaFila(pedidoSalvo, FilaImpressao.DestinoImpressao.COZINHA);

        if (pedidoSalvo.getStatusFinanceiro() == StatusFinanceiro.PAGO) {
            adicionarNaFila(pedidoSalvo, FilaImpressao.DestinoImpressao.RECIBO_CLIENTE);
        }

        return new PedidoResponseDTO(pedidoSalvo);
    }

    private BigDecimal processarItens(Pedido pedido, CheckoutRequestDTO dto) {
        BigDecimal total = BigDecimal.ZERO;

        if (dto.itens() != null && !dto.itens().isEmpty()) {
            if (dto.clienteId() != null) {
                pedido.setCliente(clienteRepository.findById(dto.clienteId())
                        .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado!")));
            }
            for (var itemDto : dto.itens()) {
                Produto produto = produtoRepository.findById(itemDto.produtoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + itemDto.produtoId()));

                ItemPedido itemPedido = criarItem(pedido, produto, itemDto.quantidade(), itemDto.observacao(), itemDto.adicionaisIds());

                // 💳 Define a comanda filha informada no checkout (ex: tablet do garçom)
                itemPedido.setNumeroConta(itemDto.numeroConta() != null ? itemDto.numeroConta() : 1);
                itemPedido.setStatusPagamento(StatusPagamento.ABERTO);

                total = total.add(calcularSubtotal(itemPedido));
                pedido.getItens().add(itemPedido);
            }
        } else {
            if (dto.clienteId() == null) throw new BusinessRuleException("Para recuperar o carrinho do banco, o clienteId e obrigatorio!");

            Carrinho carrinho = carrinhoRepository.findByClienteId(dto.clienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Carrinho nao encontrado!"));
            if (carrinho.getItens().isEmpty()) throw new BusinessRuleException("O carrinho esta vazio!");

            pedido.setCliente(carrinho.getCliente());
            for (ItemCarrinho ic : carrinho.getItens()) {
                ItemPedido itemPedido = criarItem(pedido, ic.getProduto(), ic.getQuantidade(), ic.getObservacao(), null);

                // 💳 Padrão para carrinho convencional: cai na conta principal 1
                itemPedido.setNumeroConta(1);
                itemPedido.setStatusPagamento(StatusPagamento.ABERTO);

                total = total.add(calcularSubtotal(itemPedido));
                pedido.getItens().add(itemPedido);
            }
            carrinho.getItens().clear();
            carrinhoRepository.save(carrinho);
        }
        return total;
    }

    private ItemPedido criarItem(Pedido pedido, Produto p, int qtd, String obs, List<UUID> ads) {
        ItemPedido ip = new ItemPedido();
        ip.setPedido(pedido);
        ip.setProduto(p);
        ip.setQuantidade(qtd);
        ip.setObservacaoItem(obs);
        ip.setPrecoUnitario(p.getPreco());
        if (ads != null && !ads.isEmpty()) {
            ip.setAdicionais(adicionalRepository.findAllById(ads));
        } else {
            ip.setAdicionais(new ArrayList<>());
        }
        return ip;
    }

    private BigDecimal calcularSubtotal(ItemPedido ip) {
        BigDecimal ads = ip.getAdicionais().stream()
                .map(Adicional::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ip.getPrecoUnitario().add(ads).multiply(BigDecimal.valueOf(ip.getQuantidade()));
    }

    private void adicionarNaFila(Pedido pedido, FilaImpressao.DestinoImpressao destino) {
        FilaImpressao fila = new FilaImpressao();
        fila.setPedido(pedido);
        fila.setDestino(destino);
        fila.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
        filaImpressaoRepository.save(fila);
    }

    @Transactional
    public PedidoResponseDTO receberPagamento(UUID id, PagamentoRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));

        if (pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO) throw new BusinessRuleException("Este pedido ja consta como PAGO.");
        if (pedido.getStatus() == StatusPedido.CANCELADO) throw new BusinessRuleException("Nao e possivel receber pagamento de um pedido cancelado.");

        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setValorRecebido(dto.valorRecebido());
        pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);

        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        adicionarNaFila(pedidoSalvo, FilaImpressao.DestinoImpressao.RECIBO_CLIENTE);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        return new PedidoResponseDTO(pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado.")));
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoRepository.findAll().stream().map(PedidoResponseDTO::new).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoCliente(UUID clienteId) {
        return pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId).stream()
                .map(PedidoResponseDTO::new).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPedidosAtivosMonitor() {
        List<StatusPedido> ativos = Arrays.asList(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO, StatusPedido.PRONTO);
        return pedidoRepository.findByStatusInOrderByDataHoraAsc(ativos).stream()
                .map(PedidoResponseDTO::new).collect(Collectors.toList());
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

        if (pedido.getStatus() == StatusPedido.FINALIZADO) throw new BusinessRuleException("Pedidos ja finalizados nao podem ser cancelados.");

        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setStatusFinanceiro(pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO ? StatusFinanceiro.ESTORNADO : StatusFinanceiro.CANCELADO);

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO adicionarItemPedido(UUID pedidoId, ItemPedidoRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));
        validarEdicaoPedido(pedido);

        // 💳 BLINDAGEM DA CONTA FRACIONADA: Valida se o grupo específico daquela conta filha já foi pago
        boolean contaJaPaga = pedido.getItens().stream()
                .filter(i -> i.getNumeroConta() != null && i.getNumeroConta().equals(dto.numeroConta()))
                .anyMatch(i -> i.getStatusPagamento() == StatusPagamento.PAGO);

        if (contaJaPaga) {
            throw new BusinessRuleException("Operacao Negada! A comanda filha informada (Conta " + dto.numeroConta() + ") ja foi paga e encerrada no caixa.");
        }

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado no cardapio."));

        ItemPedido novoItem = criarItem(pedido, produto, dto.quantidade(), dto.observacao(), dto.adicionaisIds());

        // Assegura que o novo item receba as propriedades da conta fracionada
        novoItem.setNumeroConta(dto.numeroConta() != null ? dto.numeroConta() : 1);
        novoItem.setStatusPagamento(StatusPagamento.ABERTO);

        pedido.getItens().add(novoItem);
        pedido.setTotal(pedido.getTotal().add(calcularSubtotal(novoItem)));

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO removerItemPedido(UUID pedidoId, UUID itemId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));
        validarEdicaoPedido(pedido);

        ItemPedido itemParaRemover = pedido.getItens().stream()
                .filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item nao encontrado nesta comanda."));

        // Impedir a remoção de itens de uma subconta que já foi paga de forma isolada
        if (itemParaRemover.getStatusPagamento() == StatusPagamento.PAGO) {
            throw new BusinessRuleException("Nao e possivel remover um item de uma comanda filha que ja foi paga.");
        }

        pedido.setTotal(pedido.getTotal().subtract(calcularSubtotal(itemParaRemover)));
        pedido.getItens().remove(itemParaRemover);

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO atualizarAdicionaisDoItem(UUID pedidoId, UUID itemId, List<UUID> adicionaisIds) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado."));
        validarEdicaoPedido(pedido);

        ItemPedido item = pedido.getItens().stream()
                .filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item nao encontrado nesta comanda."));

        if (item.getStatusPagamento() == StatusPagamento.PAGO) {
            throw new BusinessRuleException("Nao e possivel alterar os adicionais de um item pertencente a uma comanda filha ja paga.");
        }

        pedido.setTotal(pedido.getTotal().subtract(calcularSubtotal(item)));
        item.setAdicionais(adicionalRepository.findAllById(adicionaisIds));
        pedido.setTotal(pedido.getTotal().add(calcularSubtotal(item)));

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    private void validarEdicaoPedido(Pedido pedido) {
        if (pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO) {
            throw new BusinessRuleException("Nao e possivel alterar os itens de um pedido que ja foi pago.");
        }
        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.EM_ROTA) {
            throw new BusinessRuleException("Nao e possivel alterar itens de um pedido que ja esta em rota, finalizado ou cancelado.");
        }
    }

    @Transactional
    public void excluirFisicamente(UUID id) {
        throw new BusinessRuleException("Por razoes financeiras e de auditoria, pedidos nao podem ser excluidos do banco de dados. Utilize a funcao de Cancelamento.");
    }
}