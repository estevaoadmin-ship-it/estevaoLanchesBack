package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.commands.PedidoCommand;
import com.paullomaggio.estevaoLanches.dtos.CheckoutBalcaoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoMobileRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PedidoPDVService {

    private final PedidoCoreService coreService;
    private final ClienteRepository clienteRepository;
    private final CarrinhoRepository carrinhoRepository;

    public PedidoResponseDTO checkoutBalcao(CheckoutBalcaoRequestDTO dto) {
        var cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));

        Carrinho carrinho = carrinhoRepository.findByClienteId(cliente.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado."));

        PedidoCommand command = PedidoCommand.builder()
                .cliente(cliente)
                .nomeConsumidorBalcao(dto.nomeConsumidor())
                .formaPagamento(dto.formaPagamento())
                .observacao(dto.observacao())
                .tipoPedido(TipoPedido.MESA)
                .carrinhoId(carrinho.getId())
                .build();

        return coreService.processarPedido(command);
    }

    public PedidoResponseDTO processarPedidoMobile(PedidoMobileRequestDTO dto) {
        // Delega o fluxo complexo de mobile e subcontas diretamente ao Core
        return coreService.processarPedidoMobileIntegrado(dto);
    }
}