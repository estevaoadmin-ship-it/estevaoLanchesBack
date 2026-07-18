package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        List<Pagamento> pagamentosDoCaixa = pagamentoRepository.findByCaixaId(caixaAtivo.getId());
        List<EstornoPagamento> estornosDoCaixa = estornoPagamentoRepository.findByCaixaId(caixaAtivo.getId());

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

        List<MovimentacaoCaixa> todasMovimentacoes = movimentacaoCaixaRepository.findByCaixaIdAndEstornadaFalse(caixaAtivo.getId());
        BigDecimal suprimentos = todasMovimentacoes.stream().filter(m -> m.getTipo() == TipoMovimentacao.SUPRIMENTO).map(MovimentacaoCaixa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sangrias = todasMovimentacoes.stream().filter(m -> m.getTipo() == TipoMovimentacao.SANGRIA).map(MovimentacaoCaixa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEsperadoGaveta = caixaAtivo.getValorAbertura().add(faturamentoDinheiro).add(suprimentos).subtract(sangrias);

        long pedidosEmEsteira = pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);

        return new CaixaResumoResponseDTO(faturamentoTotal, faturamentoDinheiro, faturamentoPix, faturamentoCredito, faturamentoDebito, totalEsperadoGaveta, pedidosEmEsteira);
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