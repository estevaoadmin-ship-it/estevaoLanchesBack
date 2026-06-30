package com.paullomaggio.estevaoLanches.services.especialistas;

import com.paullomaggio.estevaoLanches.dtos.CheckoutBalcaoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO; // Importar CheckoutRequestDTO
import com.paullomaggio.estevaoLanches.dtos.PedidoMobileRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
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
public class PedidoPDVService {

    private final PedidoCoreService coreService;
    private final ClienteRepository clienteRepository;
    private final CarrinhoRepository carrinhoRepository; // Mantido, embora não usado diretamente aqui, pode ser usado em outras lógicas futuras.

    public PedidoResponseDTO checkoutBalcao(CheckoutBalcaoRequestDTO dto) {
        // A validação do cliente e carrinho será feita dentro do PedidoService.finalizarPedido
        // através do clienteId no CheckoutRequestDTO.

        CheckoutRequestDTO checkoutRequestDTO = new CheckoutRequestDTO(
                dto.clienteId(),
                TipoPedido.MESA, // Assumindo que PDV pode ser para mesa ou balcão, mas o exemplo usa MESA
                null, // enderecoEntrega não aplicável
                null, // numeroMesa não aplicável aqui, mas pode ser ajustado se necessário
                dto.observacao(),
                dto.nomeConsumidor(),
                null, // telefoneClienteBalcao não disponível no DTO
                dto.formaPagamento(),
                null, // valorRecebido será calculado no PedidoService
                new ArrayList<>() // itens serão buscados do carrinho no PedidoService
        );

        return coreService.finalizarPedido(checkoutRequestDTO);
    }

    public PedidoResponseDTO processarPedidoMobile(PedidoMobileRequestDTO dto) {
        // Delega o fluxo complexo de mobile e subcontas diretamente ao Core
        // O método correto no PedidoCoreService (que delega para PedidoService) é processarPedidoMobile
        return coreService.processarPedidoMobile(dto);
    }
}
