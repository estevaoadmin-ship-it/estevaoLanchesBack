package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.dtos.CheckoutDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO; // Importar CheckoutRequestDTO
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList; // Importar ArrayList

@Service
@RequiredArgsConstructor
public class PedidoDeliveryService {

    private final PedidoCoreService coreService;
    private final ClienteRepository clienteRepository;
    private final CarrinhoRepository carrinhoRepository; // Mantido, embora não usado diretamente aqui, pode ser usado em outras lógicas futuras.

    public PedidoResponseDTO checkoutDelivery(CheckoutDeliveryRequestDTO dto) {
        // A validação do cliente e carrinho será feita dentro do PedidoService.finalizarPedido
        // através do clienteId no CheckoutRequestDTO.

        CheckoutRequestDTO checkoutRequestDTO = new CheckoutRequestDTO(
                dto.clienteId(),
                TipoPedido.DELIVERY,
                dto.enderecoEntrega(),
                null, // numeroMesa não aplicável para delivery
                dto.observacao(),
                null, // nomeClienteBalcao não aplicável para delivery
                null, // telefoneClienteBalcao não aplicável para delivery
                dto.formaPagamento(),
                null, // valorRecebido será calculado no PedidoService
                new ArrayList<>() // itens serão buscados do carrinho no PedidoService
        );

        return coreService.finalizarPedido(checkoutRequestDTO);
    }
}
