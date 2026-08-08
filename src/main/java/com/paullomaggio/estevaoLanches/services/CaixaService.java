package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class CaixaService {

    private final CaixaRepository caixaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PedidoRepository pedidoRepository;
    private final ContaRepository contaRepository;
    private final MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstornoPagamentoRepository estornoPagamentoRepository;
    private final PagamentoService pagamentoService;

    // Injeção de dependências via Construtor Único Seguro
    public CaixaService(CaixaRepository caixaRepository,
                        PagamentoRepository pagamentoRepository,
                        PedidoRepository pedidoRepository,
                        ContaRepository contaRepository,
                        MovimentacaoCaixaRepository movimentacaoCaixaRepository,
                        UsuarioRepository usuarioRepository,
                        EstornoPagamentoRepository estornoPagamentoRepository,
                        PagamentoService pagamentoService) {
        this.caixaRepository = caixaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.pedidoRepository = pedidoRepository;
        this.contaRepository = contaRepository;
        this.movimentacaoCaixaRepository = movimentacaoCaixaRepository;
        this.usuarioRepository = usuarioRepository;
        this.estornoPagamentoRepository = estornoPagamentoRepository;
        this.pagamentoService = pagamentoService;
    }

    @Transactional(readOnly = true)
    public Optional<CaixaStatusResponseDTO> obterStatusAtual() {
        return caixaRepository.findByStatus(StatusCaixa.ABERTO).map(CaixaStatusResponseDTO::new);
    }

    @Transactional
    public CaixaStatusResponseDTO abrirCaixa(CaixaAberturaRequestDTO dto) {
        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação negada! Já existe um turno de caixa aberto no sistema.");
        }

        Usuario usuarioLogado = obterUsuarioGarantido();

        Caixa caixa = new Caixa();
        caixa.setStatus(StatusCaixa.ABERTO);
        caixa.setValorAbertura(dto.valorAbertura());
        caixa.setDataHoraAbertura(LocalDateTime.now());
        caixa.setUsuarioAbertura(usuarioLogado);

        return new CaixaStatusResponseDTO(caixaRepository.save(caixa));
    }

    @Transactional
    public void fecharCaixa(CaixaFechamentoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Não existe nenhum caixa aberto para ser encerrado."));

        Usuario usuarioLogado = obterUsuarioGarantido();

        caixaAtivo.setStatus(StatusCaixa.FECHADO);
        caixaAtivo.setDataHoraFechamento(LocalDateTime.now());
        caixaAtivo.setValorFechamento(dto.valorFechamento());
        caixaAtivo.setJustificativaDiferenca(dto.justificativaDiferenca());
        caixaAtivo.setUsuarioFechamento(usuarioLogado);

        caixaRepository.save(caixaAtivo);
    }

    @Transactional
    public void lancarSangria(MovimentacaoRequestDTO dto) {
        Caixa caixaAtivo = obterCaixaAbertoGarantido();

        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setCaixa(caixaAtivo);
        mov.setTipo(TipoMovimentacao.SANGRIA);
        mov.setValor(dto.valor());
        mov.setDescricao(dto.descricao().trim().toUpperCase());

        movimentacaoCaixaRepository.save(mov);
    }

    @Transactional
    public void lancarSuprimento(MovimentacaoRequestDTO dto) {
        Caixa caixaAtivo = obterCaixaAbertoGarantido();

        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setCaixa(caixaAtivo);
        mov.setTipo(TipoMovimentacao.SUPRIMENTO);
        mov.setValor(dto.valor());
        mov.setDescricao(dto.descricao().trim().toUpperCase());

        movimentacaoCaixaRepository.save(mov);
    }

    @Transactional
    public void estornarMovimentacao(UUID id, String motivo) {
        obterCaixaAbertoGarantido(); // Bloqueia alterações se o caixa geral estiver fechado

        MovimentacaoCaixa mov = movimentacaoCaixaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movimentação do livro-razão não localizada. ID: " + id));

        if (Boolean.TRUE.equals(mov.getEstornada())) {
            throw new BusinessRuleException("Esta movimentação já se encontra estornada no sistema.");
        }

        mov.setEstornada(true);
        mov.setMotivoEstorno(motivo != null ? motivo.trim().toUpperCase() : "ESTORNO SEM JUSTIFICATIVA EXTRA");
        movimentacaoCaixaRepository.save(mov);
    }

    @Transactional
    public void reabrirCaixa(UUID id, String motivo) {
        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Não é permitido reabrir um turno antigo enquanto houver um caixa ativo no salão.");
        }

        Caixa caixaTurno = caixaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno de caixa informado não localizado. ID: " + id));

        caixaTurno.setStatus(StatusCaixa.ABERTO);
        caixaTurno.setDataHoraFechamento(null);
        caixaTurno.setValorFechamento(null);
        caixaTurno.setUsuarioFechamento(null);
        caixaTurno.setMotivoReabertura(motivo != null ? motivo.trim().toUpperCase() : "REABERTURA DE TURNO EM RETAGUARDA");

        caixaRepository.save(caixaTurno);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularSaldoDevedorDaConta(UUID pedidoId, Integer numeroConta) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de origem não localizado."));

        Comanda comandaMestre = pedido.getConta().getComanda();
        Conta conta = contaRepository.findByComandaIdAndNumeroConta(comandaMestre.getId(), numeroConta)
                .orElseThrow(() -> new ResourceNotFoundException("Subconta número " + numeroConta + " não existe na mesa correspondente."));

        // --- START RESÍDUO 5 FIX ---
        BigDecimal totalBrutoPago = pagamentoRepository.sumPagamentosPorConta(conta.getId());
        if (totalBrutoPago == null) totalBrutoPago = BigDecimal.ZERO;

        BigDecimal totalEstornado = estornoPagamentoRepository.somarValorEstornadoPorContaId(conta.getId());
        if (totalEstornado == null) totalEstornado = BigDecimal.ZERO;

        BigDecimal totalLiquido = totalBrutoPago.subtract(totalEstornado);
        BigDecimal saldo = conta.getValorTotal().subtract(totalLiquido);

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, pedidoId: {}, pedidoContaId: {}, pedidoContaNumero: {}, contaId: {}, contaNumero: {}, contaValorTotalPersistido: {}, totalBrutoPago: {}, totalEstornado: {}, totalLiquido: {}, saldoCalculado: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "calcularSaldoDevedorDaConta",
                pedidoId,
                pedido.getConta() != null ? pedido.getConta().getId() : null,
                pedido.getConta() != null ? pedido.getConta().getNumeroConta() : null,
                conta.getId(),
                conta.getNumeroConta(),
                conta.getValorTotal(),
                totalBrutoPago,
                totalEstornado,
                totalLiquido,
                saldo,
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());

        return saldo.max(BigDecimal.ZERO); // Saldo nunca deve ser negativo
        // --- END RESÍDUO 5 FIX ---
    }

    @Transactional
    public void registrarPagamentoFracionado(UUID pedidoId, ContaPagamentoRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de faturamento não localizado."));

        Comanda comandaMestre = pedido.getConta().getComanda();
        Conta conta = contaRepository.findByComandaIdAndNumeroConta(comandaMestre.getId(), dto.numeroConta())
                .orElseThrow(() -> new ResourceNotFoundException("Subconta informada não ativa na mesa de atendimento."));

        // Delegação limpa para o motor unificado e centralizado de regras financeiras
        pagamentoService.registrarPagamento(conta.getId(), new PagamentoRequestDTO(dto.formaPagamento(), dto.valorRecebido()));
    }

    @Transactional(readOnly = true)
    public CaixaResumoResponseDTO obterResumoTurno() {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Não há nenhum turno de caixa ativo aberto no momento."));

        ResultadoFinanceiroCaixa resultado = calcularResumoFinanceiro(caixaAtivo);
        long pedidosEmEsteira =
                pedidoRepository.countPedidosAtivos(
                        StatusPedido.FINALIZADO,
                        StatusPedido.CANCELADO
                );
        return new CaixaResumoResponseDTO(
                resultado.faturamentoTotal(),
                resultado.faturamentoDinheiro(),
                resultado.faturamentoPix(),
                resultado.faturamentoCredito(),
                resultado.faturamentoDebito(),
                resultado.saldoEsperado(), // This maps to totalEsperadoGaveta in the DTO
                pedidosEmEsteira
        );
    }

    /**
     * Resultado financeiro compartilhado entre resumo e histórico
     */
    private record ResultadoFinanceiroCaixa(
            BigDecimal faturamentoTotal,
            BigDecimal faturamentoDinheiro,
            BigDecimal faturamentoPix,
            BigDecimal faturamentoCredito,
            BigDecimal faturamentoDebito,
            BigDecimal totalSangrias,
            long quantidadeSangrias,
            BigDecimal totalSuprimentos,
            long quantidadeSuprimentos,
            BigDecimal saldoEsperado,
            BigDecimal diferencaCaixa
    ) {}

    /**
     * Calcula os dados financeiros de um caixa específico
     * @param caixa O caixa para o qual calcular os dados financeiros
     * @return ResultadoFinanceiroCaixa com os dados financeiros calculados
     */
    private ResultadoFinanceiroCaixa calcularResumoFinanceiro(Caixa caixa) {
        List<Pagamento> pagamentosDoCaixa = pagamentoRepository.findByCaixaId(caixa.getId());
        List<EstornoPagamento> estornosDoCaixa = estornoPagamentoRepository.findByCaixaId(caixa.getId());

        BigDecimal faturamentoTotal = BigDecimal.ZERO;
        BigDecimal faturamentoDinheiro = BigDecimal.ZERO;
        BigDecimal faturamentoPix = BigDecimal.ZERO;
        BigDecimal faturamentoCredito = BigDecimal.ZERO;
        BigDecimal faturamentoDebito = BigDecimal.ZERO;

        for (Pagamento p : pagamentosDoCaixa) {
            faturamentoTotal = faturamentoTotal.add(p.getValorPago());
            if (p.getFormaPagamento() == FormaPagamento.DINHEIRO) faturamentoDinheiro = faturamentoDinheiro.add(p.getValorPago());
            if (p.getFormaPagamento() == FormaPagamento.PIX) faturamentoPix = faturamentoPix.add(p.getValorPago());
            if (p.getFormaPagamento() == FormaPagamento.CREDITO) faturamentoCredito = faturamentoCredito.add(p.getValorPago());
            if (p.getFormaPagamento() == FormaPagamento.DEBITO) faturamentoDebito = faturamentoDebito.add(p.getValorPago());
        }

        for (EstornoPagamento e : estornosDoCaixa) {
            faturamentoTotal = faturamentoTotal.subtract(e.getValorEstornado());

            Pagamento pagamentoOriginal = e.getPagamento();
            if (pagamentoOriginal != null) {
                if (pagamentoOriginal.getFormaPagamento() == FormaPagamento.DINHEIRO) faturamentoDinheiro = faturamentoDinheiro.subtract(e.getValorEstornado());
                if (pagamentoOriginal.getFormaPagamento() == FormaPagamento.PIX) faturamentoPix = faturamentoPix.subtract(e.getValorEstornado());
                if (pagamentoOriginal.getFormaPagamento() == FormaPagamento.CREDITO) faturamentoCredito = faturamentoCredito.subtract(e.getValorEstornado());
                if (pagamentoOriginal.getFormaPagamento() == FormaPagamento.DEBITO) faturamentoDebito = faturamentoDebito.subtract(e.getValorEstornado());
            }
        }

        List<MovimentacaoCaixa> todasMovimentacoes = movimentacaoCaixaRepository.findByCaixaIdAndEstornadaFalse(caixa.getId());
        BigDecimal suprimentos = todasMovimentacoes.stream().filter(m -> m.getTipo() == TipoMovimentacao.SUPRIMENTO).map(MovimentacaoCaixa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sangrias = todasMovimentacoes.stream().filter(m -> m.getTipo() == TipoMovimentacao.SANGRIA).map(MovimentacaoCaixa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEsperadoGaveta = caixa.getValorAbertura().add(faturamentoDinheiro).add(suprimentos).subtract(sangrias);
        
        // Calcular diferença de caixa apenas se houver valor de fechamento
        BigDecimal diferencaCaixa = null;
        if (caixa.getValorFechamento() != null) {
            diferencaCaixa = caixa.getValorFechamento().subtract(totalEsperadoGaveta);
        }

        // Count non-voided movements properly
        long quantidadeSangrias = 0;
        long quantidadeSuprimentos = 0;
        
        for (MovimentacaoCaixa mov : todasMovimentacoes) {
            if (mov.getTipo() == TipoMovimentacao.SANGRIA) {
                quantidadeSangrias++;
            } else if (mov.getTipo() == TipoMovimentacao.SUPRIMENTO) {
                quantidadeSuprimentos++;
            }
        }

        return new ResultadoFinanceiroCaixa(
                faturamentoTotal,
                faturamentoDinheiro,
                faturamentoPix,
                faturamentoCredito,
                faturamentoDebito,
                sangrias,
                quantidadeSangrias,
                suprimentos,
                quantidadeSuprimentos,
                totalEsperadoGaveta,
                diferencaCaixa
        );
    }

    @Transactional(readOnly = true)
    public List<CaixaHistoricoResponseDTO> obterHistorico(LocalDateTime dataInicial, LocalDateTime dataFinal) {
        List<Caixa> caixas;
        
        if (dataInicial == null && dataFinal == null) {
            caixas = caixaRepository.findByStatusOrderByDataHoraAberturaDesc(StatusCaixa.FECHADO);
        } else if (dataInicial != null && dataFinal == null) {
            caixas = caixaRepository.findByDataHoraAberturaGreaterThanEqualOrderByDataHoraAberturaDesc(dataInicial);
            // Filter to only closed caixas
            caixas = caixas.stream().filter(c -> c.getStatus() == StatusCaixa.FECHADO).toList();
        } else if (dataInicial == null && dataFinal != null) {
            caixas = caixaRepository.findByDataHoraAberturaLessThanEqualOrderByDataHoraAberturaDesc(dataFinal);
            // Filter to only closed caixas
            caixas = caixas.stream().filter(c -> c.getStatus() == StatusCaixa.FECHADO).toList();
        } else {
            caixas = caixaRepository.findByDataHoraAberturaBetweenOrderByDataHoraAberturaDesc(dataInicial, dataFinal);
            // Filter to only closed caixas
            caixas = caixas.stream().filter(c -> c.getStatus() == StatusCaixa.FECHADO).toList();
        }
        
        return caixas.stream()
                .map(this::converterParaHistoricoResponse)
                .toList();
    }

    private CaixaHistoricoResponseDTO converterParaHistoricoResponse(Caixa caixa) {
        ResultadoFinanceiroCaixa resultado = calcularResumoFinanceiro(caixa);
        
        return new CaixaHistoricoResponseDTO(
                caixa.getId(),
                caixa.getStatus(),
                caixa.getDataHoraAbertura(),
                caixa.getDataHoraFechamento(),
                caixa.getUsuarioAbertura() != null ? caixa.getUsuarioAbertura().getNome() : null,
                caixa.getUsuarioFechamento() != null ? caixa.getUsuarioFechamento().getNome() : null,
                caixa.getValorAbertura(),
                caixa.getValorFechamento(),
                resultado.faturamentoDinheiro(),
                resultado.faturamentoPix(),
                resultado.faturamentoCredito(),
                resultado.faturamentoDebito(),
                resultado.faturamentoTotal(),
                resultado.totalSangrias(),
                resultado.quantidadeSangrias(),
                resultado.totalSuprimentos(),
                resultado.quantidadeSuprimentos(),
                resultado.saldoEsperado(),
                resultado.diferencaCaixa(),
                caixa.getJustificativaDiferenca()
        );
    }

    private Caixa obterCaixaAbertoGarantido() {
        return caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Operação bloqueada! O caixa geral está fechado no momento."));
    }

    private Usuario obterUsuarioGarantido() {
        return usuarioRepository.findAll().stream().findFirst().orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNome("ESTEVAO ADMINISTRADOR");
            u.setEmail("admin@estevaolanches.com");
            return usuarioRepository.save(u);
        });
    }
}
