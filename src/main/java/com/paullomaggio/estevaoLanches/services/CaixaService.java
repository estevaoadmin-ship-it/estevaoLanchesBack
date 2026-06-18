package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class CaixaService {

    @Autowired private CaixaRepository caixaRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    @Autowired private AuditoriaCaixaRepository auditoriaCaixaRepository;
    @Autowired private PagamentoRepository pagamentoRepository;

    // ==========================================
    // 💳 LOGICA DE CONTAS FRACIONADAS (NOVA)
    // ==========================================

    @Transactional(readOnly = true)
    public BigDecimal calcularSaldoDevedorDaConta(UUID pedidoId, Integer numeroConta) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new BusinessRuleException("Pedido não encontrado"));

        BigDecimal totalConta = pedido.getItens().stream()
                .filter(i -> i.getNumeroConta() != null && i.getNumeroConta().equals(numeroConta))
                .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal jaPago = pagamentoRepository.sumPagamentosPorConta(pedidoId, numeroConta);
        if (jaPago == null) jaPago = BigDecimal.ZERO;

        return totalConta.subtract(jaPago);
    }

    @Transactional
    public void registrarPagamentoFracionado(UUID pedidoId, ContaPagamentoRequestDTO dto) {
        BigDecimal saldoDevedor = calcularSaldoDevedorDaConta(pedidoId, dto.getNumeroConta());

        if (dto.getValorPago().compareTo(saldoDevedor) > 0) {
            throw new BusinessRuleException("Valor pago (R$ " + dto.getValorPago() + ") excede o saldo devedor desta conta (R$ " + saldoDevedor + ")!");
        }

        Pagamento pag = new Pagamento();
        pag.setPedidoId(pedidoId);
        pag.setNumeroConta(dto.getNumeroConta());
        pag.setValorPago(dto.getValorPago());
        pag.setFormaPagamento(dto.getFormaPagamento());
        pag.setUsuarioResponsavel(obterUsuarioLogado().getNome());
        pagamentoRepository.save(pag);

        if (saldoDevedor.subtract(dto.getValorPago()).compareTo(BigDecimal.ZERO) == 0) {
            Pedido pedido = pedidoRepository.findById(pedidoId)
                    .orElseThrow(() -> new BusinessRuleException("Pedido não encontrado"));

            pedido.getItens().stream()
                    .filter(i -> i.getNumeroConta() != null && i.getNumeroConta().equals(dto.getNumeroConta()))
                    .forEach(i -> i.setStatusPagamento(StatusPagamento.PAGO));

            boolean pedidoFinalizado = pedido.getItens().stream()
                    .allMatch(i -> StatusPagamento.PAGO.equals(i.getStatusPagamento()));

            if (pedidoFinalizado) {
                pedido.setStatus(StatusPedido.FINALIZADO);
            }

            pedidoRepository.save(pedido);
        }
    }

    // ==========================================
    // ⚙️ GESTÃO DO CAIXA E AUDITORIA (EXISTENTES)
    // ==========================================

    @Transactional(readOnly = true)
    public Optional<CaixaStatusResponseDTO> obterStatusAtual() {
        return caixaRepository.findByStatus(StatusCaixa.ABERTO).map(CaixaStatusResponseDTO::new);
    }

    @Transactional(readOnly = true)
    public CaixaResumoResponseDTO obterResumoTurno() {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Não existe um caixa ativo."));

        LocalDateTime inicio = caixaAtivo.getDataHoraAbertura();
        UUID caixaId = caixaAtivo.getId();

        BigDecimal dinheiro = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.DINHEIRO, StatusPedido.FINALIZADO);
        BigDecimal pix = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.PIX, StatusPedido.FINALIZADO);
        BigDecimal credito = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.CREDITO, StatusPedido.FINALIZADO);
        BigDecimal debito = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.DEBITO, StatusPedido.FINALIZADO);

        BigDecimal totalSuprimentos = movimentacaoCaixaRepository.somarPorCaixaETipo(caixaId, TipoMovimentacao.SUPRIMENTO);
        BigDecimal totalSangrias = movimentacaoCaixaRepository.somarPorCaixaETipo(caixaId, TipoMovimentacao.SANGRIA);

        BigDecimal faturamentoTotal = dinheiro.add(pix).add(credito).add(debito);
        BigDecimal totalEsperadoGaveta = caixaAtivo.getValorAbertura().add(dinheiro).add(totalSuprimentos).subtract(totalSangrias);
        long pedidosEmEsteira = pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);

        return new CaixaResumoResponseDTO(faturamentoTotal, dinheiro, pix, credito, debito, totalEsperadoGaveta, pedidosEmEsteira);
    }

    @Transactional
    public CaixaStatusResponseDTO abrirCaixa(CaixaAberturaRequestDTO dto) {
        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) throw new BusinessRuleException("Caixa já aberto.");
        Usuario funcionarioLogado = obterUsuarioLogado();
        Caixa caixa = new Caixa();
        caixa.setValorAbertura(dto.valorAbertura());
        caixa.setStatus(StatusCaixa.ABERTO);
        caixa.setDataHoraAbertura(LocalDateTime.now());
        caixa.setUsuarioAbertura(funcionarioLogado);
        Caixa caixaSalvo = caixaRepository.save(caixa);
        registrarMovimentacaoInterna(caixaSalvo, TipoMovimentacao.ABERTURA, MotivoMovimentacao.OUTROS, dto.valorAbertura(), "Carga inicial.", funcionarioLogado);
        registrarAuditoria(caixaSalvo.getId(), "CAIXA_ABERTO", funcionarioLogado, null, "Valor: R$ " + dto.valorAbertura());
        return new CaixaStatusResponseDTO(caixaSalvo);
    }

    @Transactional
    public void fecharCaixa(CaixaFechamentoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO).orElseThrow(() -> new BusinessRuleException("Nenhum caixa aberto."));
        Usuario funcionarioLogado = obterUsuarioLogado();
        BigDecimal esperado = obterResumoTurno().totalEsperadoGaveta();
        BigDecimal diferenca = dto.valorFechamento().subtract(esperado);

        if (diferenca.compareTo(BigDecimal.ZERO) != 0 && (dto.justificativaDiferenca() == null || dto.justificativaDiferenca().isBlank())) {
            throw new BusinessRuleException("Justificativa obrigatória para diferença de caixa.");
        }

        if (diferenca.compareTo(BigDecimal.ZERO) < 0) registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.QUEBRA_NEGATIVA, MotivoMovimentacao.OUTROS, diferenca.abs(), dto.justificativaDiferenca(), funcionarioLogado);
        else if (diferenca.compareTo(BigDecimal.ZERO) > 0) registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.QUEBRA_POSITIVA, MotivoMovimentacao.OUTROS, diferenca, dto.justificativaDiferenca(), funcionarioLogado);

        registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.FECHAMENTO, MotivoMovimentacao.OUTROS, dto.valorFechamento(), "Encerramento.", funcionarioLogado);
        caixaAtivo.setStatus(StatusCaixa.FECHADO);
        caixaAtivo.setValorFechamento(dto.valorFechamento());
        caixaAtivo.setDataHoraFechamento(LocalDateTime.now());
        caixaAtivo.setUsuarioFechamento(funcionarioLogado);
        caixaRepository.save(caixaAtivo);
        registrarAuditoria(caixaAtivo.getId(), "CAIXA_FECHADO", funcionarioLogado, "Esperado: R$ " + esperado, "Contado: R$ " + dto.valorFechamento());
    }

    @Transactional
    public void lancarSangria(MovimentacaoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO).orElseThrow(() -> new BusinessRuleException("Nenhum caixa aberto."));
        BigDecimal saldoGaveta = obterResumoTurno().totalEsperadoGaveta();
        if (dto.valor().compareTo(saldoGaveta) > 0) throw new BusinessRuleException("Saldo insuficiente.");
        registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.SANGRIA, dto.motivo(), dto.valor(), dto.observacao(), obterUsuarioLogado());
    }

    @Transactional
    public void lancarSuprimento(MovimentacaoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO).orElseThrow(() -> new BusinessRuleException("Nenhum caixa aberto."));
        registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.SUPRIMENTO, dto.motivo(), dto.valor(), dto.observacao(), obterUsuarioLogado());
    }

    @Transactional
    public void estornarMovimentacao(UUID movimentacaoId, String motivo) {
        MovimentacaoCaixa m = movimentacaoCaixaRepository.findById(movimentacaoId).orElseThrow(() -> new BusinessRuleException("Movimentação não encontrada."));
        if (m.getCancelada()) throw new BusinessRuleException("Já estornado.");
        m.setCancelada(true);
        m.setCanceladoPor(obterUsuarioLogado());
        m.setDataHoraCancelamento(LocalDateTime.now());
        m.setMotivoCancelamento(motivo);
        movimentacaoCaixaRepository.save(m);
        registrarAuditoria(m.getCaixa().getId(), "ESTORNO", obterUsuarioLogado(), null, motivo);
    }

    @Transactional
    public void reabrirCaixa(UUID caixaId, String motivo) {
        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) throw new BusinessRuleException("Já existe caixa aberto.");
        Caixa caixaAlvo = caixaRepository.findById(caixaId).orElseThrow(() -> new BusinessRuleException("Não encontrado."));
        caixaAlvo.setStatus(StatusCaixa.ABERTO);
        caixaAlvo.setValorFechamento(null);
        caixaRepository.save(caixaAlvo);
        registrarAuditoria(caixaAlvo.getId(), "REABERTURA", obterUsuarioLogado(), null, motivo);
    }

    private Usuario obterUsuarioLogado() {
        return (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private MovimentacaoCaixa registrarMovimentacaoInterna(Caixa caixa, TipoMovimentacao tipo, MotivoMovimentacao motivo, BigDecimal valor, String obs, Usuario u) {
        MovimentacaoCaixa m = new MovimentacaoCaixa();
        m.setCaixa(caixa);
        m.setTipo(tipo);
        m.setMotivo(motivo);
        m.setValor(valor);
        m.setObservacao(obs);
        m.setUsuario(u);
        m.setDataHora(LocalDateTime.now());
        m.setCancelada(false);
        return movimentacaoCaixaRepository.save(m);
    }

    private void registrarAuditoria(UUID caixaId, String acao, Usuario u, String antes, String depois) {
        AuditoriaCaixa aud = new AuditoriaCaixa();
        aud.setCaixaId(caixaId);
        aud.setAcao(acao);
        aud.setUsuario(u);
        aud.setDataHora(LocalDateTime.now());
        aud.setDadosAntes(antes);
        aud.setDadosDepois(depois);
        auditoriaCaixaRepository.save(aud);
    }
}