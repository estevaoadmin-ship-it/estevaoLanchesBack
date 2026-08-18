package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ContaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ContaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ContaService {

    private final ContaRepository contaRepository;
    private final ComandaRepository comandaRepository;

    // Injeção de dependência via construtor
    public ContaService(ContaRepository contaRepository, ComandaRepository comandaRepository) {
        this.contaRepository = contaRepository;
        this.comandaRepository = comandaRepository;
    }

    /**
     * 🎯 REAJUSTADO: Cria uma subconta atrelada à Comanda Mestre e inicializa seu respectivo Cliente 1:1.
     */
    @Transactional
    public ContaResponseDTO criar(ContaRequestDTO dto) {
        Comanda comanda = comandaRepository.findById(dto.comandaId())
                .orElseThrow(() -> new ResourceNotFoundException("Comanda mestre não localizada. ID: " + dto.comandaId()));

        contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), dto.numeroConta())
                .ifPresent(c -> {
                    throw new BusinessRuleException("Esta mesa já possui a subconta " + dto.numeroConta() + " ativa.");
                });

        Conta conta = new Conta();
        conta.setNumeroConta(dto.numeroConta());
        conta.setComanda(comanda);
        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setPago, Conta: {}, numeroConta: {}, comandaId: {}, valorTotal: {}, pagoAnterior: {}, pagoNovo: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "criar",
                conta.getId(),
                conta.getNumeroConta(),
                comanda.getId(),
                BigDecimal.ZERO,
                null,
                false,
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        conta.setPago(false);
        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pré-save criação da conta, Conta: {}, numeroConta: {}, comandaId: {}, valorTotal: {}, pago: {}, hashCode: {}, identityHashCode: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "criar",
                conta.getId(),
                conta.getNumeroConta(),
                comanda.getId(),
                BigDecimal.ZERO,
                conta.getPago(),
                conta.hashCode(),
                System.identityHashCode(conta),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        conta.setValorTotal(BigDecimal.ZERO);

        Conta contaSalva = contaRepository.save(conta);

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pós-save criação da conta, Conta: {}, numeroConta: {}, comandaId: {}, valorTotal: {}, pago: {}, hashCode: {}, identityHashCode: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "criar",
                contaSalva.getId(),
                contaSalva.getNumeroConta(),
                comanda.getId(),
                contaSalva.getValorTotal(),
                contaSalva.getPago(),
                contaSalva.hashCode(),
                System.identityHashCode(contaSalva),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());

        return new ContaResponseDTO(contaSalva);
    }

    @Transactional(readOnly = true)
    public ContaResponseDTO buscarPorId(UUID id) {
        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada com o ID informado."));
        return new ContaResponseDTO(conta);
    }

    @Transactional(readOnly = true)
    public List<ContaResponseDTO> listarPorComanda(UUID comandaId) {
        if (!comandaRepository.existsById(comandaId)) {
            throw new ResourceNotFoundException("Comanda mestre não localizada.");
        }
        return contaRepository.findByComandaId(comandaId).stream()
                .map(ContaResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletar(UUID id) {
        Conta conta = contaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada para exclusão."));

        if (Boolean.FALSE.equals(conta.getPago()) && conta.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("Não é possível excluir uma conta com saldo devedor pendente.");
        }
        contaRepository.delete(conta);
    }

    /**
     * Sincroniza o valor total da Conta com a soma dos valores de todos os Pedidos associados a ela.
     * Esta rotina recalcula o valor total da conta do zero, garantindo a integridade dos dados.
     *
     * @param contaId O ID da Conta a ser sincronizada.
     * @throws ResourceNotFoundException se a Conta não for encontrada.
     */
    @Transactional
    public void sincronizarValorTotal(UUID contaId) {
        Conta conta = contaRepository.findById(contaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada com o ID informado para sincronização."));

        // Utiliza a coleção de pedidos já existente na entidade Conta
        // Apenas pedidos com status financeiro AGUARDANDO_PAGAMENTO contribuem para o valor total da conta
        BigDecimal total = conta.getPedidos().stream()
                .filter(pedido -> pedido.getStatusFinanceiro() == StatusFinanceiro.AGUARDANDO_PAGAMENTO)
                .map(Pedido::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setValorTotal, Conta: {}, numeroConta: {}, valorAnterior: {}, valorNovo: {}, pedidosCount: {}, thread: {}, transactionAtiva: {}, stacktrace: {}",
                getClass().getSimpleName(),
                "sincronizarValorTotal",
                conta.getId(),
                conta.getNumeroConta(),
                conta.getValorTotal(),
                total,
                conta.getPedidos() != null ? conta.getPedidos().size() : 0,
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                stacktraceResumido());

        conta.setValorTotal(total);

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pré-save sincronização, Conta: {}, numeroConta: {}, valorTotal: {}, pago: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "sincronizarValorTotal",
                conta.getId(),
                conta.getNumeroConta(),
                conta.getValorTotal(),
                conta.getPago(),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());

        contaRepository.save(conta);

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pós-save sincronização, Conta: {}, numeroConta: {}, valorTotal: {}, pago: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "sincronizarValorTotal",
                conta.getId(),
                conta.getNumeroConta(),
                conta.getValorTotal(),
                conta.getPago(),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
    }

    private String stacktraceResumido() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .skip(3)
                .limit(8)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" | "));
    }
}
