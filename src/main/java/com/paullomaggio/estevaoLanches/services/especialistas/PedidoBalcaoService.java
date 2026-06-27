package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.commands.PedidoCommand;
import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PedidoBalcaoService {

    private final PedidoCoreService coreService;

    public PedidoResponseDTO checkoutBalcao(CheckoutBalcaoRequestDTO dto, java.util.UUID carrinhoId) {

        PedidoCommand command = PedidoCommand.builder()
                .nomeConsumidorBalcao(dto.nomeConsumidor())
                .formaPagamento(dto.formaPagamento())
                .observacao(dto.observacao())
                .tipoPedido(TipoPedido.BALCAO)
                .carrinhoId(carrinhoId)
                .build();

        return coreService.processarPedido(command);
    }
}