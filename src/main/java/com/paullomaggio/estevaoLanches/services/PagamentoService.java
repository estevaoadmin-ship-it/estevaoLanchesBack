package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Conta;
import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ContaRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PagamentoService {

    @Autowired private PagamentoRepository pagamentoRepository;
    @Autowired private ContaRepository contaRepository;

    /**
     * Registra uma entrada financeira para uma conta específica.
     * Realiza a auditoria matemática automática para quitação da subconta.
     */
    @Transactional
    public PagamentoResponseDTO registrarPagamento(UUID contaId, PagamentoRequestDTO dto) {
        Conta conta = contaRepository.findById(contaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta de destino não localizada. ID: " + contaId));

        if (Boolean.TRUE.equals(conta.getPago())) {
            throw new BusinessRuleException("Operação negada! Esta conta já se encontra totalmente quitada.");
        }

        BigDecimal valorLancamento = dto.valorRecebido();
        if (valorLancamento == null || valorLancamento.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("O valor do pagamento deve ser maior que zero.");
        }

        // Calcula o teto disponível para pagamento para impedir recebimento excedente por engano
        BigDecimal totalJaPago = pagamentoRepository.sumPagamentosPorConta(contaId);
        BigDecimal saldoDevedor = conta.getValorTotal().subtract(totalJaPago);

        if (valorLancamento.compareTo(saldoDevedor) > 0 && dto.formaPagamento() != com.paullomaggio.estevaoLanches.enums.FormaPagamento.DINHEIRO) {
            throw new BusinessRuleException("Valor informado (R$ " + valorLancamento + ") excede o saldo devedor atual (R$ " + saldoDevedor + ").");
        }

        // Instancia o registro físico da transação
        Pagamento pagamento = new Pagamento();
        pagamento.setConta(conta);
        pagamento.setValorPago(valorLancamento);
        pagamento.setFormaPagamento(dto.formaPagamento());
        pagamento.setDataHora(LocalDateTime.now());
        pagamento.setUsuarioResponsavel("SISTEMA_MOBILE"); // Ajustável conforme segurança futuramente

        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);

        // Reavalia a quitação total da conta após o commit do lançamento corrente
        BigDecimal totalAtualizadoPago = totalJaPago.add(valorLancamento);
        if (totalAtualizadoPago.compareTo(conta.getValorTotal()) >= 0) {
            conta.setPago(true);
            contaRepository.save(conta);
        }

        return new PagamentoResponseDTO(pagamentoSalvo);
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponseDTO> listarPorConta(UUID contaId) {
        if (!contaRepository.existsById(contaId)) {
            throw new ResourceNotFoundException("Conta não localizada.");
        }
        return pagamentoRepository.findByContaId(contaId).stream()
                .map(PagamentoResponseDTO::new)
                .collect(Collectors.toList());
    }
}