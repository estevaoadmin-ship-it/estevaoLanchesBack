package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRetiradaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.services.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Service
@RequiredArgsConstructor
@Validated
public class PedidoRetiradaService {

    private final PedidoService pedidoService;

    public PedidoResponseDTO checkoutRetirada(@Valid CheckoutRetiradaRequestDTO dto) {
        return pedidoService.finalizarRetirada(dto);
    }
}