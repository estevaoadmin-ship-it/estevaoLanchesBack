package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.commands.PedidoCommand;
import com.paullomaggio.estevaoLanches.dtos.CheckoutDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoDeliveryService {

    private final PedidoCoreService coreService;
    private final ClienteRepository clienteRepository;
    private final CarrinhoRepository carrinhoRepository;

    public PedidoResponseDTO checkoutDelivery(CheckoutDeliveryRequestDTO dto) {
        var cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));

        Carrinho carrinho = carrinhoRepository.findByClienteId(cliente.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado."));

        PedidoCommand command = PedidoCommand.builder()
                .cliente(cliente)
                .enderecoEntrega(dto.enderecoEntrega())
                .formaPagamento(dto.formaPagamento())
                .observacao(dto.observacao())
                .tipoPedido(TipoPedido.DELIVERY)
                .carrinhoId(carrinho.getId())
                .build();

        return coreService.processarPedido(command);
    }
}