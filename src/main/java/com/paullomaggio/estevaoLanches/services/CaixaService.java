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

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private MovimentacaoCaixaRepository movimentacaoCaixaRepository;

    @Autowired
    private AuditoriaCaixaRepository auditoriaCaixaRepository;

    @Transactional(readOnly = true)
    public Optional<CaixaStatusResponseDTO> obterStatusAtual() {
        return caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .map(CaixaStatusResponseDTO::new);
    }

    @Transactional(readOnly = true)
    public CaixaResumoResponseDTO obterResumoTurno() {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Não existe um caixa ativo no momento para coletar indicadores."));

        LocalDateTime inicio = caixaAtivo.getDataHoraAbertura();
        UUID caixaId = caixaAtivo.getId();

        // 🚀 CORREÇÃO APLICADA AQUI: Usando o Enum em vez de String!
        BigDecimal dinheiro = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.DINHEIRO, StatusPedido.FINALIZADO);
        BigDecimal pix = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.PIX, StatusPedido.FINALIZADO);
        BigDecimal credito = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.CREDITO, StatusPedido.FINALIZADO);
        BigDecimal debito = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, FormaPagamento.DEBITO, StatusPedido.FINALIZADO);

        // 2. Coleta fluxo interno de suprimentos e sangrias ativos no livro-razão
        BigDecimal totalSuprimentos = movimentacaoCaixaRepository.somarPorCaixaETipo(caixaId, TipoMovimentacao.SUPRIMENTO);
        BigDecimal totalSangrias = movimentacaoCaixaRepository.somarPorCaixaETipo(caixaId, TipoMovimentacao.SANGRIA);

        // 3. Fórmula do Saldo Esperado: Abertura + Vendas em Dinheiro + Suprimentos - Sangrias
        BigDecimal faturamentoTotal = dinheiro.add(pix).add(credito).add(debito);
        BigDecimal totalEsperadoGaveta = caixaAtivo.getValorAbertura()
                .add(dinheiro)
                .add(totalSuprimentos)
                .subtract(totalSangrias);

        long pedidosEmEsteira = pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);

        return new CaixaResumoResponseDTO(
                faturamentoTotal, dinheiro, pix, credito, debito, totalEsperadoGaveta, pedidosEmEsteira
        );
    }

    @Transactional
    public CaixaStatusResponseDTO abrirCaixa(CaixaAberturaRequestDTO dto) {
        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação Negada! Já existe um turno de caixa ativo no sistema.");
        }

        Usuario funcionarioLogado = obterUsuarioLogado();

        Caixa caixa = new Caixa();
        caixa.setValorAbertura(dto.valorAbertura());
        caixa.setStatus(StatusCaixa.ABERTO);
        caixa.setDataHoraAbertura(LocalDateTime.now());
        caixa.setUsuarioAbertura(funcionarioLogado);
        Caixa caixaSalvo = caixaRepository.save(caixa);

        // Lançamento automático de abertura no livro-razão
        registrarMovimentacaoInterna(caixaSalvo, TipoMovimentacao.ABERTURA, MotivoMovimentacao.OUTROS, dto.valorAbertura(), "Carga inicial de troco.", funcionarioLogado);

        // Rastreabilidade na tabela de auditoria
        registrarAuditoria(caixaSalvo.getId(), "CAIXA_ABERTO", funcionarioLogado, null, "Status: ABERTO, Valor Inicial: R$ " + dto.valorAbertura());

        return new CaixaStatusResponseDTO(caixaSalvo);
    }

    @Transactional
    public void fecharCaixa(CaixaFechamentoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Erro operacional! Nenhum caixa aberto localizado para encerramento."));

        Usuario funcionarioLogado = obterUsuarioLogado();

        // Validação matemática do Fechamento Cego
        CaixaResumoResponseDTO resumo = obterResumoTurno();
        BigDecimal esperado = resumo.totalEsperadoGaveta();
        BigDecimal contado = dto.valorFechamento();
        BigDecimal diferenca = contado.subtract(esperado);

        // Bloqueio caso haja quebra sem justificativa explicada
        if (diferenca.compareTo(BigDecimal.ZERO) != 0 && (dto.justificativaDiferenca() == null || dto.justificativaDiferenca().trim().isBlank())) {
            throw new BusinessRuleException("Diferença de caixa detectada (R$ " + diferenca + "). É obrigatório preencher uma justificativa de auditoria.");
        }

        // Mapeia automaticamente quebras positivas ou negativas no livro-razão
        if (diferenca.compareTo(BigDecimal.ZERO) < 0) {
            registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.QUEBRA_NEGATIVA, MotivoMovimentacao.OUTROS, diferenca.abs(), "Falta de caixa. Justificativa: " + dto.justificativaDiferenca(), funcionarioLogado);
        } else if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
            registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.QUEBRA_POSITIVA, MotivoMovimentacao.OUTROS, diferenca, "Sobra de caixa. Justificativa: " + dto.justificativaDiferenca(), funcionarioLogado);
        }

        registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.FECHAMENTO, MotivoMovimentacao.OUTROS, contado, "Encerramento de expediente.", funcionarioLogado);

        caixaAtivo.setStatus(StatusCaixa.FECHADO);
        caixaAtivo.setValorFechamento(contado);
        caixaAtivo.setDataHoraFechamento(LocalDateTime.now());
        caixaAtivo.setUsuarioFechamento(funcionarioLogado);
        caixaRepository.save(caixaAtivo);

        registrarAuditoria(caixaAtivo.getId(), "CAIXA_FECHADO", funcionarioLogado, "Esperado: R$ " + esperado, "Contado: R$ " + contado + " | Diferença: R$ " + diferenca);
    }

    @Transactional
    public void lancarSangria(MovimentacaoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Impossível realizar sangria. Não há nenhum caixa aberto."));

        Usuario funcionarioLogado = obterUsuarioLogado();

        // Blindagem contra sangrias maiores que o saldo físico real em dinheiro na gaveta
        BigDecimal saldoGaveta = obterResumoTurno().totalEsperadoGaveta();
        if (dto.valor().compareTo(saldoGaveta) > 0) {
            throw new BusinessRuleException("Sangria rejeitada! O valor solicitado (R$ " + dto.valor() + ") é superior ao saldo disponível na gaveta (R$ " + saldoGaveta + ").");
        }

        MovimentacaoCaixa m = registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.SANGRIA, dto.motivo(), dto.valor(), dto.observacao(), funcionarioLogado);
        registrarAuditoria(caixaAtivo.getId(), "SANGRIA_REALIZADA", funcionarioLogado, null, "ID Movimentacao: " + m.getId() + " | Valor: R$ " + dto.valor());
    }

    @Transactional
    public void lancarSuprimento(MovimentacaoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Impossível realizar suprimento. Não há nenhum caixa aberto."));

        Usuario funcionarioLogado = obterUsuarioLogado();

        MovimentacaoCaixa m = registrarMovimentacaoInterna(caixaAtivo, TipoMovimentacao.SUPRIMENTO, dto.motivo(), dto.valor(), dto.observacao(), funcionarioLogado);
        registrarAuditoria(caixaAtivo.getId(), "SUPRIMENTO_REALIZADO", funcionarioLogado, null, "ID Movimentacao: " + m.getId() + " | Valor: R$ " + dto.valor());
    }

    @Transactional
    public void estornarMovimentacao(UUID movimentacaoId, String motivoEstorno) {
        MovimentacaoCaixa m = movimentacaoCaixaRepository.findById(movimentacaoId)
                .orElseThrow(() -> new BusinessRuleException("Movimentação não encontrada para estorno."));

        if (m.getCancelada()) {
            throw new BusinessRuleException("Esta movimentação financeira já foi estornada anteriormente.");
        }
        if (m.getTipo() == TipoMovimentacao.ABERTURA || m.getTipo() == TipoMovimentacao.FECHAMENTO) {
            throw new BusinessRuleException("Por segurança, movimentações estruturais de Abertura/Fechamento não podem ser canceladas isoladamente.");
        }

        Usuario funcionarioLogado = obterUsuarioLogado();
        m.setCancelada(true);
        m.setCanceladoPor(funcionarioLogado);
        m.setDataHoraCancelamento(LocalDateTime.now());
        m.setMotivoCancelamento(motivoEstorno);
        movimentacaoCaixaRepository.save(m);

        registrarAuditoria(m.getCaixa().getId(), "MOVIMENTACAO_CANCELADA", funcionarioLogado, "Valor cancelado: R$ " + m.getValor(), "Motivo estorno: " + motivoEstorno);
    }

    @Transactional
    public void reabrirCaixa(UUID caixaId, String motivoReabertura) {
        Usuario funcionarioLogado = obterUsuarioLogado();

        // Verificação estrita de privilégio administrativo
        if (funcionarioLogado.getRole() == null || !funcionarioLogado.getRole().toString().contains("ADMIN")) {
            throw new BusinessRuleException("Acesso negado! A reabertura de turnos fechados é restrita a administradores.");
        }

        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Bloqueio de segurança! Não é possível reabrir este caixa pois já existe outro turno ativo no momento.");
        }

        Caixa caixaAlvo = caixaRepository.findById(caixaId)
                .orElseThrow(() -> new BusinessRuleException("Caixa não localizado no banco de dados."));

        if (caixaAlvo.getStatus() == StatusCaixa.ABERTO) {
            throw new BusinessRuleException("Este caixa já está aberto.");
        }

        String dadosAntes = "Status: FECHADO | Fechamento Contado: R$ " + caixaAlvo.getValorFechamento();

        caixaAlvo.setStatus(StatusCaixa.ABERTO);
        caixaAlvo.setValorFechamento(null);
        caixaAlvo.setDataHoraFechamento(null);
        caixaAlvo.setUsuarioFechamento(null);
        caixaRepository.save(caixaAlvo);

        registrarAuditoria(caixaAlvo.getId(), "CAIXA_REABERTO", funcionarioLogado, dadosAntes, "Status alterado para ABERTO. Motivo: " + motivoReabertura);
    }

    // --- ENCAPSULAMENTOS DE SUPORTE OPERACIONAL ---
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