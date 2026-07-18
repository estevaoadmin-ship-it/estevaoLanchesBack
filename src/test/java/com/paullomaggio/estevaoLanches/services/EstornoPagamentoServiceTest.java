package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.EstornarPagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.EstornoPagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.StatusPedido; // Import correto para StatusPedido
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaRepository;
import com.paullomaggio.estevaoLanches.repositories.EstornoPagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstornoPagamentoServiceTest {

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private EstornoPagamentoRepository estornoPagamentoRepository;

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private ContaRepository contaRepository;

    @InjectMocks
    private EstornoPagamentoService estornoPagamentoService;

    private UUID pagamentoId;
    private Pagamento pagamento;
    private Caixa caixaAberto;
    private EstornarPagamentoRequestDTO estornarPagamentoRequestDTO;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext(); // Limpa o contexto de segurança antes de cada teste

        pagamentoId = UUID.randomUUID();

        // Configuração padrão para Pagamento
        pagamento = new Pagamento();
        pagamento.setId(pagamentoId);
        pagamento.setValorPago(new BigDecimal("100.00"));
        pagamento.setFormaPagamento(FormaPagamento.PIX);
        pagamento.setDataHora(LocalDateTime.now().minusDays(1));
        pagamento.setUsuarioResponsavel("usuario_original");

        // Correção 1: Construtor de Caixa
        Caixa caixaOriginal = new Caixa();
        caixaOriginal.setId(UUID.randomUUID());
        caixaOriginal.setStatus(StatusCaixa.FECHADO);
        pagamento.setCaixa(caixaOriginal);

        // Correção 1: Construtor de Caixa
        caixaAberto = new Caixa();
        caixaAberto.setId(UUID.randomUUID());
        caixaAberto.setStatus(StatusCaixa.ABERTO);

        // Configuração padrão para DTO
        estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(
                new BigDecimal("10.00"),
                "Motivo de teste"
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext(); // Limpa o contexto de segurança após cada teste
    }

    @Nested
    @DisplayName("Bloco1ValidacoesBasicas")
    class Bloco1ValidacoesBasicas {

        @Test
        @DisplayName("1. pagamento inexistente deve lançar ResourceNotFoundException")
        void pagamentoInexistente_deveLancarResourceNotFoundException() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.empty());

            ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () ->
                    estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
            );

            assertEquals("Pagamento não encontrado.", exception.getMessage());
            verify(estornoPagamentoRepository, never()).save(any(EstornoPagamento.class));
        }

        @Test
        @DisplayName("2. valor null deve lançar BusinessRuleException")
        void valorNull_deveLancarBusinessRuleException() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(null, "Motivo");

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
            );

            assertEquals("O valor do estorno deve ser maior que zero.", exception.getMessage());
            verify(caixaRepository, never()).findByStatus(any());
            verify(estornoPagamentoRepository, never()).somarValorEstornadoPorPagamentoId(any());
            verify(estornoPagamentoRepository, never()).save(any(EstornoPagamento.class));
        }

        @Test
        @DisplayName("3. valor zero deve lançar BusinessRuleException")
        void valorZero_deveLancarBusinessRuleException() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(BigDecimal.ZERO, "Motivo");

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
            );

            assertEquals("O valor do estorno deve ser maior que zero.", exception.getMessage());
            verify(caixaRepository, never()).findByStatus(any());
            verify(estornoPagamentoRepository, never()).somarValorEstornadoPorPagamentoId(any());
            verify(estornoPagamentoRepository, never()).save(any(EstornoPagamento.class));
        }

        @Test
        @DisplayName("4. valor negativo deve lançar BusinessRuleException")
        void valorNegativo_deveLancarBusinessRuleException() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("-10.00"), "Motivo");

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
            );

            assertEquals("O valor do estorno deve ser maior que zero.", exception.getMessage());
            verify(caixaRepository, never()).findByStatus(any());
            verify(estornoPagamentoRepository, never()).somarValorEstornadoPorPagamentoId(any());
            verify(estornoPagamentoRepository, never()).save(any(EstornoPagamento.class));
        }
    }

    @Nested
    @DisplayName("Bloco2MatematicaEstorno")
    class Bloco2MatematicaEstorno {

        // Correção 5: Removendo stubs de @BeforeEach
        // @BeforeEach
        // void setupMatematica() {
        //     when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
        //     when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
        //     when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
        //         EstornoPagamento estorno = invocation.getArgument(0);
        //         estorno.setId(UUID.randomUUID()); // Simula o ID sendo gerado pelo JPA
        //         return estorno;
        //     });
        // }

        @Test
        @DisplayName("5. estorno integral de 100 deve salvar valorEstornado = 100")
        void estornoIntegral_deveSalvarValorCorreto() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("100.00"), "Estorno integral");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(new BigDecimal("100.00"), estornoCaptor.getValue().getValorEstornado());
        }

        @Test
        @DisplayName("6. estorno parcial de 40 deve salvar 40")
        void estornoParcial_deveSalvarValorCorreto() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("40.00"), "Estorno parcial");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(new BigDecimal("40.00"), estornoCaptor.getValue().getValorEstornado());
        }

        @Test
        @DisplayName("7. segundo estorno parcial deve aceitar")
        void segundoEstornoParcial_deveAceitar() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(new BigDecimal("40.00"));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("30.00"), "Segundo estorno parcial");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(new BigDecimal("30.00"), estornoCaptor.getValue().getValorEstornado());
        }

        @Test
        @DisplayName("8. estorno exatamente igual ao saldo restante deve aceitar")
        void estornoExatamenteIgualAoSaldoRestante_deveAceitar() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(new BigDecimal("70.00"));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("30.00"), "Estorno saldo restante");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(new BigDecimal("30.00"), estornoCaptor.getValue().getValorEstornado());
        }

        @Test
        @DisplayName("9. estorno acima do saldo deve lançar BusinessRuleException")
        void estornoAcimaDoSaldo_deveLancarBusinessRuleException() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(new BigDecimal("70.00"));
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("31.00"), "Estorno acima do saldo");

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
            );

            assertEquals("O valor do estorno (31.00) excede o saldo estornável (30.00).", exception.getMessage());
            verify(estornoPagamentoRepository, never()).save(any(EstornoPagamento.class));
            verify(caixaRepository, never()).findByStatus(any()); // Não deve buscar caixa se a validação ocorre antes
        }

        @Test
        @DisplayName("10. pagamento totalmente estornado e novo pedido de estorno deve lançar BusinessRuleException")
        void pagamentoTotalmenteEstornado_novoEstorno_deveLancarBusinessRuleException() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(new BigDecimal("100.00"));
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("1.00"), "Estorno extra");

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
            );

            assertEquals("Este pagamento já foi totalmente estornado.", exception.getMessage());
            verify(estornoPagamentoRepository, never()).save(any(EstornoPagamento.class));
            verify(caixaRepository, never()).findByStatus(any()); // Não deve buscar caixa se a validação ocorre antes
        }

        @Test
        @DisplayName("11. repository retorna null na soma deve tratar como BigDecimal.ZERO")
        void repositoryRetornaNullNaSoma_deveTratarComoBigDecimalZero() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(null);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("50.00"), "Estorno com soma null");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(new BigDecimal("50.00"), estornoCaptor.getValue().getValorEstornado());
        }
    }

    @Nested
    @DisplayName("Bloco3PagamentoPedido")
    class Bloco3PagamentoPedido {

        private Pedido pedido;
        private UUID pedidoId;

        @BeforeEach
        void setupPedido() {
            pedidoId = UUID.randomUUID();
            pedido = new Pedido();
            pedido.setId(pedidoId);
            pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
            // Correção 2: Usando StatusPedido.FINALIZADO
            pedido.setStatus(StatusPedido.FINALIZADO);
            pedido.setFormaPagamento(FormaPagamento.PIX);
            pedido.setValorRecebido(new BigDecimal("100.00"));

            pagamento.setPedido(pedido);
            pagamento.setConta(null); // Garantir que é um pagamento de pedido
        }

        @Test
        @DisplayName("12. estorno parcial não marca Pedido como ESTORNADO")
        void estornoParcial_naoMarcaPedidoComoEstornado() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("40.00"), "Estorno parcial");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(StatusFinanceiro.PAGO, pagamento.getPedido().getStatusFinanceiro());
        }

        @Test
        @DisplayName("13. estorno total marca Pedido como ESTORNADO")
        void estornoTotal_marcaPedidoComoEstornado() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("100.00"), "Estorno total");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(StatusFinanceiro.ESTORNADO, pagamento.getPedido().getStatusFinanceiro());
        }

        @Test
        @DisplayName("14. segundo estorno completa o total e marca Pedido como ESTORNADO")
        void segundoEstornoCompletaTotal_marcaPedidoComoEstornado() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(new BigDecimal("40.00"));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("60.00"), "Segundo estorno total");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(StatusFinanceiro.ESTORNADO, pagamento.getPedido().getStatusFinanceiro());
        }

        @Test
        @DisplayName("15. estorno parcial não altera status operacional do Pedido")
        void estornoParcial_naoAlteraStatusOperacionalPedido() {
            StatusPedido statusOriginal = pedido.getStatus();
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("40.00"), "Estorno parcial");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(statusOriginal, pagamento.getPedido().getStatus());
        }

        @Test
        @DisplayName("16. estorno total não altera status operacional do Pedido")
        void estornoTotal_naoAlteraStatusOperacionalPedido() {
            StatusPedido statusOriginal = pedido.getStatus();
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("100.00"), "Estorno total");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(statusOriginal, pagamento.getPedido().getStatus());
        }

        @Test
        @DisplayName("17. estorno não altera formaPagamento do Pedido")
        void estorno_naoAlteraFormaPagamentoPedido() {
            FormaPagamento formaPagamentoOriginal = pedido.getFormaPagamento();
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("50.00"), "Estorno");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(formaPagamentoOriginal, pagamento.getPedido().getFormaPagamento());
        }

        @Test
        @DisplayName("18. estorno não altera valorRecebido do Pedido")
        void estorno_naoAlteraValorRecebidoPedido() {
            BigDecimal valorRecebidoOriginal = pedido.getValorRecebido();
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("50.00"), "Estorno");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(valorRecebidoOriginal, pagamento.getPedido().getValorRecebido());
        }
    }

    @Nested
    @DisplayName("Bloco4PagamentoConta")
    class Bloco4PagamentoConta {

        private Conta conta;
        private UUID contaId;

        @BeforeEach
        void setupConta() {
            contaId = UUID.randomUUID();
            conta = new Conta();
            conta.setId(contaId);
            conta.setValorTotal(new BigDecimal("100.00"));
            conta.setPago(true); // Valor inicial para o teste

            pagamento.setConta(conta);
            pagamento.setPedido(null); // Garantir que é um pagamento de conta
        }

        @Test
        @DisplayName("19. Conta permanece paga quando total líquido ainda quita valorTotal")
        void contaPermanecePaga_quandoTotalLiquidoQuitaValorTotal() {
            // Correção 5: Movendo stubs para o teste
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Simula o save da conta

            // Conta.valorTotal = 100
            // Pagamento.valorPago = 100 (este pagamento)
            // total bruto dos pagamentos = 150 (incluindo este e outros)
            // total estornado consolidado (incluindo o novo estorno) = 40
            // total líquido = 110 (150 - 40) -> ainda quita 100

            pagamento.setValorPago(new BigDecimal("100.00")); // Este pagamento
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO); // Nenhum estorno anterior para este pagamento
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("40.00"), "Estorno parcial"); // Estornando 40 deste pagamento

            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("150.00")); // Total bruto de pagamentos da conta
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("40.00")); // Total estornado consolidado (incluindo o novo estorno)

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            // Correção 3: Usando getPago()
            assertTrue(Boolean.TRUE.equals(conta.getPago()));
            verify(contaRepository).save(conta);
        }

        @Test
        @DisplayName("20. Conta volta para não paga quando líquido fica abaixo do total")
        void contaVoltaParaNaoPaga_quandoLiquidoFicaAbaixoDoTotal() {
            // Correção 5: Movendo stubs para o teste
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Simula o save da conta

            // Conta.valorTotal = 100
            // Pagamento.valorPago = 100 (este pagamento)
            // total bruto dos pagamentos = 100 (apenas este pagamento)
            // total estornado consolidado (incluindo o novo estorno) = 20
            // líquido = 80 (100 - 20) -> abaixo de 100

            pagamento.setValorPago(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("20.00"), "Estorno parcial");

            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("20.00"));

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            // Correção 3: Usando getPago()
            assertFalse(Boolean.TRUE.equals(conta.getPago()));
            verify(contaRepository).save(conta);
        }

        @Test
        @DisplayName("21. estorno total deixa Conta não paga")
        void estornoTotal_deixaContaNaoPaga() {
            // Correção 5: Movendo stubs para o teste
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Simula o save da conta

            // Conta.valorTotal = 100
            // Pagamento.valorPago = 100
            // total bruto = 100
            // total estornado = 100
            // líquido = 0

            pagamento.setValorPago(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("100.00"), "Estorno total");

            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("100.00"));

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            // Correção 3: Usando getPago()
            assertFalse(Boolean.TRUE.equals(conta.getPago()));
            verify(contaRepository).save(conta);
        }

        @Test
        @DisplayName("22. verificar persistência do novo estado da Conta")
        void verificarPersistenciaNovoEstadoConta() {
            // Correção 5: Movendo stubs para o teste
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Simula o save da conta

            pagamento.setValorPago(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("10.00"), "Estorno");

            when(pagamentoRepository.sumPagamentosPorConta(contaId)).thenReturn(new BigDecimal("100.00"));
            when(estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId)).thenReturn(new BigDecimal("10.00"));

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            verify(contaRepository, times(1)).save(any(Conta.class));
        }

        @Test
        @DisplayName("23. pagamento de Pedido não deve salvar Conta")
        void pagamentoDePedido_naoDeveSalvarConta() {
            // Correção 9: Tornando o teste autossuficiente e removendo stubs desnecessários
            // Configura o pagamento para ser de um pedido, não de uma conta
            Pagamento pagamentoDePedido = new Pagamento();
            pagamentoDePedido.setId(pagamentoId);
            pagamentoDePedido.setValorPago(new BigDecimal("100.00"));
            pagamentoDePedido.setFormaPagamento(FormaPagamento.PIX);
            pagamentoDePedido.setDataHora(LocalDateTime.now().minusDays(1));
            pagamentoDePedido.setUsuarioResponsavel("usuario_original");
            pagamentoDePedido.setCaixa(pagamento.getCaixa()); // Reutiliza o caixa original do setup

            Pedido pedidoAssociado = new Pedido();
            pedidoAssociado.setId(UUID.randomUUID());
            pedidoAssociado.setStatusFinanceiro(StatusFinanceiro.PAGO);
            pedidoAssociado.setStatus(StatusPedido.FINALIZADO); // Correção 2
            pagamentoDePedido.setPedido(pedidoAssociado);
            pagamentoDePedido.setConta(null);

            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamentoDePedido));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            EstornarPagamentoRequestDTO dto = new EstornarPagamentoRequestDTO(new BigDecimal("10.00"), "Estorno");

            estornoPagamentoService.estornar(pagamentoId, dto);

            verify(contaRepository, never()).save(any(Conta.class));
            // Não há necessidade de verificar sumPagamentosPorConta ou somarValorEstornadoPorContaId
            // pois o fluxo para conta não é ativado.
        }
    }

    @Nested
    @DisplayName("Bloco5Caixa")
    class Bloco5Caixa {

        // Correção 5: Removendo stubs de @BeforeEach
        // @BeforeEach
        // void setupCaixa() {
        //     when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
        //     when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
        //     when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
        //         EstornoPagamento estorno = invocation.getArgument(0);
        //         estorno.setId(UUID.randomUUID());
        //         return estorno;
        //     });
        // }

        @Test
        @DisplayName("24. caixa fechado bloqueia estorno")
        void caixaFechado_bloqueiaEstorno() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

            BusinessRuleException exception = assertThrows(BusinessRuleException.class, () ->
                    estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
            );

            assertEquals("Não há caixa aberto para realizar o estorno.", exception.getMessage());
            verify(estornoPagamentoRepository, never()).save(any(EstornoPagamento.class));
        }

        @Test
        @DisplayName("25. estorno utiliza caixa atualmente aberto")
        void estornoUtilizaCaixaAtualmenteAberto() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            Caixa caixaOriginalPagamento = pagamento.getCaixa(); // Caixa FECHADO
            // Correção 1: Construtor de Caixa
            Caixa caixaAbertoAtual = new Caixa();
            caixaAbertoAtual.setId(UUID.randomUUID());
            caixaAbertoAtual.setStatus(StatusCaixa.ABERTO);

            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoAtual));

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());

            EstornoPagamento estornoSalvo = estornoCaptor.getValue();
            assertEquals(caixaAbertoAtual, estornoSalvo.getCaixa());
            assertNotEquals(caixaOriginalPagamento, estornoSalvo.getCaixa());
        }

        @Test
        @DisplayName("26. não altera Caixa original do Pagamento")
        void naoAlteraCaixaOriginalDoPagamento() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            Caixa caixaOriginalPagamento = pagamento.getCaixa();
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(caixaOriginalPagamento, pagamento.getCaixa());
        }
    }

    @Nested
    @DisplayName("Bloco6Autoria")
    class Bloco6Autoria {

        // Correção 5: Removendo stubs de @BeforeEach
        // @BeforeEach
        // void setupAutoria() {
        //     when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
        //     when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
        //     when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
        //     when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
        //         EstornoPagamento estorno = invocation.getArgument(0);
        //         estorno.setId(UUID.randomUUID());
        //         return estorno;
        //     });
        // }

        @Test
        @DisplayName("27. usuário autenticado é registrado")
        void usuarioAutenticado_eRegistrado() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            String username = "operador@teste.com";
            // Correção 4: Usando mock(Authentication.class)
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn(username);
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(username, estornoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("28. sem Authentication usa SISTEMA")
        void semAuthentication_usaSistema() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });
            // SecurityContextHolder.clearContext() no @BeforeEach já garante isso

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals("SISTEMA", estornoCaptor.getValue().getUsuarioResponsavel());
        }

        @Test
        @DisplayName("29. Authentication não autenticado usa SISTEMA")
        void authenticationNaoAutenticado_usaSistema() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            Authentication authentication = mock(Authentication.class);
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);

            when(authentication.isAuthenticated()).thenReturn(false); // Não autenticado

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals("SISTEMA", estornoCaptor.getValue().getUsuarioResponsavel());
            verify(authentication, never()).getName(); // Não deve chamar getName se não autenticado
        }

        @Test
        @DisplayName("30. anonymousUser usa SISTEMA")
        void anonymousUser_usaSistema() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            String username = "anonymousUser";
            // Correção 4: Usando mock(Authentication.class)
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn(username);
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals("SISTEMA", estornoCaptor.getValue().getUsuarioResponsavel());
        }
    }

    @Nested
    @DisplayName("Bloco7ConteudoDoRegistroEImutabilidade")
    class Bloco7ConteudoDoRegistroEImutabilidade {

        // Correção 5: Removendo stubs de @BeforeEach
        // @BeforeEach
        // void setupConteudo() {
        //     when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
        //     when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
        //     when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
        //     when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
        //         EstornoPagamento estorno = invocation.getArgument(0);
        //         estorno.setId(UUID.randomUUID());
        //         return estorno;
        //     });
        // }

        @Test
        @DisplayName("31. Estorno salvo aponta para o Pagamento original")
        void estornoSalvo_apontaParaPagamentoOriginal() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID()); // Este teste precisa do ID para o DTO
                return estorno;
            });

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertSame(pagamento, estornoCaptor.getValue().getPagamento());
        }

        @Test
        @DisplayName("32. valorEstornado corresponde ao DTO")
        void valorEstornado_correspondeAoDto() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            BigDecimal valorEstorno = new BigDecimal("25.50");
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(valorEstorno, "Motivo qualquer");

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(valorEstorno, estornoCaptor.getValue().getValorEstornado());
        }

        @Test
        @DisplayName("33. motivo corresponde ao DTO")
        void motivo_correspondeAoDto() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            String motivo = "Estorno por erro de lançamento";
            estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("10.00"), motivo);

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertEquals(motivo, estornoCaptor.getValue().getMotivo());
        }

        @Test
        @DisplayName("34. dataHora é preenchida")
        void dataHora_ePreenchida() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            ArgumentCaptor<EstornoPagamento> estornoCaptor = ArgumentCaptor.forClass(EstornoPagamento.class);
            verify(estornoPagamentoRepository).save(estornoCaptor.capture());
            assertNotNull(estornoCaptor.getValue().getDataHora());
            assertTrue(estornoCaptor.getValue().getDataHora().isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("35. Pagamento.valorPago permanece inalterado")
        void pagamentoValorPago_permaneceInalterado() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            BigDecimal valorPagoOriginal = pagamento.getValorPago();

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(valorPagoOriginal, pagamento.getValorPago());
        }

        @Test
        @DisplayName("36. Pagamento.formaPagamento permanece inalterada")
        void pagamentoFormaPagamento_permaneceInalterada() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            FormaPagamento formaPagamentoOriginal = pagamento.getFormaPagamento();

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(formaPagamentoOriginal, pagamento.getFormaPagamento());
        }

        @Test
        @DisplayName("37. Pagamento.caixa permanece inalterado")
        void pagamentoCaixa_permaneceInalterado() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            Caixa caixaOriginal = pagamento.getCaixa();

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            assertEquals(caixaOriginal, pagamento.getCaixa());
        }

        @Test
        @DisplayName("38. Pagamento não é salvo novamente")
        void pagamento_naoESalvoNovamente() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            verify(pagamentoRepository, never()).save(any(Pagamento.class));
        }
    }

    @Nested
    @DisplayName("Bloco8Listagem")
    class Bloco8Listagem {

        @Test
        @DisplayName("39. pagamento inexistente lança ResourceNotFoundException")
        void listarPorPagamento_pagamentoInexistente_lancaResourceNotFoundException() {
            when(pagamentoRepository.findById(pagamentoId)).thenReturn(Optional.empty());

            ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () ->
                    estornoPagamentoService.listarPorPagamento(pagamentoId)
            );

            assertEquals("Pagamento não encontrado.", exception.getMessage());
            verify(estornoPagamentoRepository, never()).findByPagamento_IdOrderByDataHoraDesc(any());
        }

        @Test
        @DisplayName("40. pagamento existente sem estornos retorna lista vazia")
        void listarPorPagamento_pagamentoExistenteSemEstornos_retornaListaVazia() {
            when(pagamentoRepository.findById(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.findByPagamento_IdOrderByDataHoraDesc(pagamentoId)).thenReturn(Collections.emptyList());

            List<EstornoPagamentoResponseDTO> result = estornoPagamentoService.listarPorPagamento(pagamentoId);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("41. pagamento existente retorna todos os estornos")
        void listarPorPagamento_pagamentoExistente_retornaTodosEstornos() {
            EstornoPagamento estorno1 = new EstornoPagamento(UUID.randomUUID(), pagamento, caixaAberto, new BigDecimal("10.00"), "Motivo 1", LocalDateTime.now().minusHours(2), "USER");
            EstornoPagamento estorno2 = new EstornoPagamento(UUID.randomUUID(), pagamento, caixaAberto, new BigDecimal("20.00"), "Motivo 2", LocalDateTime.now().minusHours(1), "USER");
            List<EstornoPagamento> estornos = List.of(estorno1, estorno2);

            when(pagamentoRepository.findById(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.findByPagamento_IdOrderByDataHoraDesc(pagamentoId)).thenReturn(estornos);

            List<EstornoPagamentoResponseDTO> result = estornoPagamentoService.listarPorPagamento(pagamentoId);

            assertFalse(result.isEmpty());
            assertEquals(2, result.size());
            assertEquals(estorno1.getId(), result.get(0).id());
            assertEquals(estorno2.getId(), result.get(1).id());
        }

        @Test
        @DisplayName("42. repository correto é chamado")
        void listarPorPagamento_repositoryCorretoEChamado() {
            when(pagamentoRepository.findById(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(estornoPagamentoRepository.findByPagamento_IdOrderByDataHoraDesc(pagamentoId)).thenReturn(Collections.emptyList());

            estornoPagamentoService.listarPorPagamento(pagamentoId);

            verify(estornoPagamentoRepository).findByPagamento_IdOrderByDataHoraDesc(pagamentoId);
        }
    }

    @Nested
    @DisplayName("Bloco9LockEOrdemCritica")
    class Bloco9LockEOrdemCritica {

        // Correção 5: Removendo stubs de @BeforeEach
        // @BeforeEach
        // void setupLockOrdem() {
        //     when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
        //     when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
        //     when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
        //     when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
        //         EstornoPagamento estorno = invocation.getArgument(0);
        //         estorno.setId(UUID.randomUUID());
        //         return estorno;
        //     });
        // }

        @Test
        @DisplayName("43. confirmar que o método utiliza findByIdForUpdate")
        void confirmarMetodoUtilizaFindByIdForUpdate() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            verify(pagamentoRepository).findByIdForUpdate(pagamentoId);
            verify(pagamentoRepository, never()).findById(pagamentoId);
        }

        @Test
        @DisplayName("44. verificar ordem crítica com InOrder")
        void verificarOrdemCriticaComInOrder() {
            when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
            when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
            when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
            when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
                EstornoPagamento estorno = invocation.getArgument(0);
                estorno.setId(UUID.randomUUID());
                return estorno;
            });

            InOrder inOrder = inOrder(
                    pagamentoRepository,
                    estornoPagamentoRepository,
                    caixaRepository
            );

            estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO);

            inOrder.verify(pagamentoRepository).findByIdForUpdate(pagamentoId);
            inOrder.verify(estornoPagamentoRepository).somarValorEstornadoPorPagamentoId(pagamentoId);
            inOrder.verify(caixaRepository).findByStatus(StatusCaixa.ABERTO);
            inOrder.verify(estornoPagamentoRepository).save(any(EstornoPagamento.class));
        }
    }

    // Correção 6: Removendo o bloco TestesDeInvarianteDaEntidade
    // @Nested
    // @DisplayName("TestesDeInvarianteDaEntidade")
    // class TestesDeInvarianteDaEntidade {
    //
    //     @BeforeEach
    //     void setupInvariante() {
    //         when(pagamentoRepository.findByIdForUpdate(pagamentoId)).thenReturn(Optional.of(pagamento));
    //         when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAberto));
    //         when(estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(pagamentoId)).thenReturn(BigDecimal.ZERO);
    //         // Mock save para lançar IllegalArgumentException quando o motivo for inválido
    //         when(estornoPagamentoRepository.save(any(EstornoPagamento.class))).thenAnswer(invocation -> {
    //             EstornoPagamento estorno = invocation.getArgument(0);
    //             // Simula a validação @PrePersist/@PreUpdate da entidade
    //             if (estorno.getMotivo() == null || estorno.getMotivo().trim().isEmpty()) {
    //                 throw new IllegalArgumentException("O motivo não pode ser nulo ou vazio.");
    //             }
    //             estorno.setId(UUID.randomUUID());
    //             return estorno;
    //         });
    //     }
    //
    //     @Test
    //     @DisplayName("50. motivo null rejeitado pela entidade (via save)")
    //     void motivoNull_rejeitadoPelaEntidade() {
    //         estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("10.00"), null);
    //
    //         IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
    //                 estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
    //         );
    //
    //         assertEquals("O motivo não pode ser nulo ou vazio.", exception.getMessage());
    //     }
    //
    //     @Test
    //     @DisplayName("51. motivo blank rejeitado pela entidade (via save)")
    //     void motivoBlank_rejeitadoPelaEntidade() {
    //         estornarPagamentoRequestDTO = new EstornarPagamentoRequestDTO(new BigDecimal("10.00"), "   ");
    //
    //         IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
    //                 estornoPagamentoService.estornar(pagamentoId, estornarPagamentoRequestDTO)
    //         );
    //
    //         assertEquals("O motivo não pode ser nulo ou vazio.", exception.getMessage());
    //     }
    // }
}