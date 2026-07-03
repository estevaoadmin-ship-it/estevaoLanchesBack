package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.dtos.CheckoutDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.services.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Service
@RequiredArgsConstructor
@Validated
public class PedidoDeliveryService {

    private final PedidoService pedidoService;

    public PedidoResponseDTO checkoutDelivery(@Valid CheckoutDeliveryRequestDTO dto) {
        return pedidoService.finalizarDelivery(dto);
    }
}