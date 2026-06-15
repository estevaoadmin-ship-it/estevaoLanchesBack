package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CaixaAberturaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaFechamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaResumoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaStatusResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CaixaService {

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private PedidoRepository pedidoRepository; // 🚨 INJETADO

    @Transactional(readOnly = true)
    public Optional<CaixaStatusResponseDTO> obterStatusAtual() {
        return caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .map(CaixaStatusResponseDTO::new);
    }

    // 🚀 NOVO MÉTODO: Consolida a matemática financeira de todo o expediente atual
    @Transactional(readOnly = true)
    public CaixaResumoResponseDTO obterResumoTurno() {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Operação cancelada! Não existe um caixa ativo no momento para coletar indicadores."));

        LocalDateTime inicio = caixaAtivo.getDataHoraAbertura();

        // Agregação de faturamento fatiado
        BigDecimal dinheiro = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, "DINHEIRO", StatusPedido.FINALIZADO);
        BigDecimal pix = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, "PIX", StatusPedido.FINALIZADO);
        BigDecimal credito = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, "CREDITO", StatusPedido.FINALIZADO);
        BigDecimal debito = pedidoRepository.somarFaturamentoPorTurnoEForma(inicio, "DEBITO", StatusPedido.FINALIZADO);

        // Cálculos operacionais matemáticos
        BigDecimal faturamentoTotal = dinheiro.add(pix).add(credito).add(debito);
        BigDecimal totalEsperadoGaveta = caixaAtivo.getValorAbertura().add(dinheiro); // Troco inicial + entradas em espécie

        long pedidosEmEsteira = pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);

        return new CaixaResumoResponseDTO(
                faturamentoTotal,
                dinheiro,
                pix,
                credito,
                debito,
                totalEsperadoGaveta,
                pedidosEmEsteira
        );
    }

    @Transactional
    public CaixaStatusResponseDTO abrirCaixa(CaixaAberturaRequestDTO dto) {
        if (caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação Negada! Já existe um turno de caixa ativo no sistema.");
        }

        Usuario funcionarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Caixa caixa = new Caixa();
        caixa.setValorAbertura(dto.valorAbertura());
        caixa.setStatus(StatusCaixa.ABERTO);
        caixa.setDataHoraAbertura(LocalDateTime.now());
        caixa.setUsuarioAbertura(funcionarioLogado);

        Caixa caixaSalvo = caixaRepository.save(caixa);
        return new CaixaStatusResponseDTO(caixaSalvo);
    }

    @Transactional
    public void fecharCaixa(CaixaFechamentoRequestDTO dto) {
        Caixa caixaAtivo = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Erro operacional! Não foi localizado nenhum caixa aberto para fechamento."));

        Usuario funcionarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        caixaAtivo.setStatus(StatusCaixa.FECHADO);
        caixaAtivo.setValorFechamento(dto.valorFechamento());
        caixaAtivo.setDataHoraFechamento(LocalDateTime.now());
        caixaAtivo.setUsuarioFechamento(funcionarioLogado);

        caixaRepository.save(caixaAtivo);
    }
}