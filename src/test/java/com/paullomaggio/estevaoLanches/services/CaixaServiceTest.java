package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ContaPagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Testes de Serviço — CaixaService")
class CaixaServiceTest {

    @Mock private CaixaRepository caixaRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private PagamentoRepository pagamentoRepository;

    @InjectMocks private CaixaService caixaService;

    @Test
    @DisplayName("CENÁRIO D: Deve rejeitar pagamento se a subconta informada já estiver marcada como paga")
    void deveRejeitarPagamentoAcimaDoSaldoDaConta() {
        UUID pedidoId = UUID.randomUUID();
        Comanda comanda = new Comanda(); comanda.setId(UUID.randomUUID());

        // 🎯 FIX DEFINITIVO: Configura a conta como PAGO = TRUE para disparar a BusinessRuleException esperada pelo teste
        Conta contaJaPaga = new Conta(UUID.randomUUID(), 1, true, new BigDecimal("50.00"), comanda, null, new ArrayList<>(), new ArrayList<>());

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setConta(contaJaPaga);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(contaRepository.findByComandaIdAndNumeroConta(any(UUID.class), eq(1))).thenReturn(Optional.of(contaJaPaga));

        ContaPagamentoRequestDTO dtoInvalido = new ContaPagamentoRequestDTO(1, new BigDecimal("10.00"), FormaPagamento.CREDITO);

        assertThrows(BusinessRuleException.class, () ->
                caixaService.registrarPagamentoFracionado(pedidoId, dtoInvalido)
        );
    }
}