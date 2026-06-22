package com.paullomaggio.estevaoLanches.resiliency;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoRequestDTO;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.repositories.FilaImpressaoRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import com.paullomaggio.estevaoLanches.services.PedidoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // 🎯 FIX: Nova importação da API de Bean Overrides

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class PedidoRollbackIntegrationTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @MockitoBean // 🎯 FIX: Substituído o depreciado @MockBean pelo moderno @MockitoBean
    private FilaImpressaoRepository filaImpressaoRepository;

    @Test
    @DisplayName("🛡️ RESILIÊNCIA: Se a inserção na Fila de Impressão falhar no checkout, o Pedido Comercial deve sofrer Rollback total")
    void deveGarantirAtomicidadeERollbackDoPedidoAoFalharInfraestrutura() {
        UUID produtoValidoId = UUID.fromString("54fcf4be-8c36-4b8f-a83b-4bc7a1ceb0f5");

        List<ItemPedidoRequestDTO> itens = List.of(new ItemPedidoRequestDTO(produtoValidoId, 1, "Gelo e limão", null, 1));
        CheckoutRequestDTO dtoCheckout = new CheckoutRequestDTO(
                null, TipoPedido.RETIRADA, null, null, null,
                "TESTE ROLLBACK ATOMICO", null, FormaPagamento.DINHEIRO, new BigDecimal("5.00"), itens
        );

        long totalPedidosAntes = pedidoRepository.count();

        // 💥 Sabotagem controlada na infraestrutura mocado
        doThrow(new RuntimeException("PANE CATASTRÓFICA: Falha física ao gravar na tabela de impressão"))
                .when(filaImpressaoRepository).save(any(FilaImpressao.class));

        assertThrows(RuntimeException.class, () -> pedidoService.finalizarPedido(dtoCheckout));

        // O pedido não pode ter sido salvo de forma órfã. O contador precisa continuar igual.
        long totalPedidosDepois = pedidoRepository.count();
        assertThat(totalPedidosDepois).isEqualTo(totalPedidosAntes);
        System.out.println("[BLINDAGEM 🛡️] Teste de Rollback Atômico concluído com a nova API @MockitoBean.");
    }
}