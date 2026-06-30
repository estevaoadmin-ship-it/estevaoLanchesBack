package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.dtos.CheckoutBalcaoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO; // Importar CheckoutRequestDTO
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList; // Importar ArrayList

@Service
@RequiredArgsConstructor
public class PedidoBalcaoService {

    private final PedidoCoreService coreService;

    // Removido o parâmetro 'carrinhoId' pois o finalizarPedido usa o clienteId para encontrar o carrinho.
    public PedidoResponseDTO checkoutBalcao(CheckoutBalcaoRequestDTO dto) {

        CheckoutRequestDTO checkoutRequestDTO = new CheckoutRequestDTO(
                dto.clienteId(),
                TipoPedido.BALCAO,
                null, // enderecoEntrega não aplicável para balcão
                null, // numeroMesa não aplicável para balcão
                dto.observacao(),
                dto.nomeConsumidor(),
                null, // telefoneClienteBalcao não disponível no DTO
                dto.formaPagamento(),
                null, // valorRecebido será calculado no PedidoService
                new ArrayList<>() // itens serão buscados do carrinho no PedidoService
        );

        return coreService.finalizarPedido(checkoutRequestDTO);
    }
}
