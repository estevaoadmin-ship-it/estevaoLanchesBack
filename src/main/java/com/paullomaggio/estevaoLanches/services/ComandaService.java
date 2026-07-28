package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ComandaService {

    private final ComandaRepository comandaRepository;
    private final MesaRepository mesaRepository;
    private final ContaRepository contaRepository;

    private static final UUID EMPRESA_PADRAO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FILIAL_PADRAO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public ComandaService(ComandaRepository comandaRepository, MesaRepository mesaRepository, ContaRepository contaRepository) {
        this.comandaRepository = comandaRepository;
        this.mesaRepository = mesaRepository;
        this.contaRepository = contaRepository;
    }

    /**
     * Abre a comanda da mesa. Instancia a comanda mãe e injeta automaticamente
     * a primeira Conta (Conta 1) contendo um cliente padrão da sessão.
     */
    @Transactional
    public ComandaResponseDTO abrirPorNumeroMesa(Integer numeroMesa) {
        // ✅ Validação de piso e teto: Resolve CT-INT-004, 005 e 006
        if (numeroMesa == null || numeroMesa <= 0 || numeroMesa > 500) {
            throw new BusinessRuleException("Número de mesa inválido.");
        }

        // ✅ Lógica de criação sob demanda mantida para não quebrar testes E2E
        Mesa mesa = mesaRepository.findByNumero(numeroMesa)
                .orElseGet(() -> {
                    Mesa novaMesa = new Mesa();
                    novaMesa.setNumero(numeroMesa);
                    novaMesa.setStatus(StatusMesa.LIVRE);
                    novaMesa.setEmpresaId(EMPRESA_PADRAO);
                    novaMesa.setFilialId(FILIAL_PADRAO);
                    return mesaRepository.save(novaMesa);
                });

        Optional<Comanda> comandaExistente = comandaRepository.findByMesaNumeroAndStatus(numeroMesa, StatusComanda.ABERTA);
        if (comandaExistente.isPresent()) {
            return new ComandaResponseDTO(comandaExistente.get(), true);
        }

        mesa.setStatus(StatusMesa.OCUPADA);
        mesaRepository.save(mesa);

        Comanda comanda = new Comanda();
        comanda.setMesa(mesa);
        comanda.setStatus(StatusComanda.ABERTA);
        comanda.setAbertaEm(LocalDateTime.now());
        comanda.setEmpresaId(EMPRESA_PADRAO);
        comanda.setFilialId(FILIAL_PADRAO);
        Comanda comandaSalva = comandaRepository.save(comanda);

        Conta contaPai = new Conta();
        contaPai.setComanda(comandaSalva);
        contaPai.setNumeroConta(1);
        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setPago, conta: {}, numeroConta: {}, comandaId: {}, valorTotal: {}, pagoAnterior: {}, pagoNovo: {}, thread: {}, transactionAtiva: {}, stacktrace: {}",
                getClass().getSimpleName(),
                "abrirPorNumeroMesa",
                contaPai.getId(),
                contaPai.getNumeroConta(),
                comandaSalva.getId(),
                BigDecimal.ZERO,
                null,
                false,
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                java.util.Arrays.stream(Thread.currentThread().getStackTrace()).skip(3).limit(8).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining(" | ")));
        contaPai.setPago(false);
        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setValorTotal, conta: {}, numeroConta: {}, comandaId: {}, valorAnterior: {}, valorNovo: {}, pago: {}, hashCode: {}, identityHashCode: {}, thread: {}, transactionAtiva: {}, stacktrace: {}",
                getClass().getSimpleName(),
                "abrirPorNumeroMesa",
                contaPai.getId(),
                contaPai.getNumeroConta(),
                comandaSalva.getId(),
                null,
                BigDecimal.ZERO,
                contaPai.getPago(),
                contaPai.hashCode(),
                System.identityHashCode(contaPai),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                java.util.Arrays.stream(Thread.currentThread().getStackTrace()).skip(3).limit(8).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining(" | ")));
        contaPai.setValorTotal(BigDecimal.ZERO);

        contaRepository.save(contaPai);

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pós-save criação da conta, conta: {}, numeroConta: {}, comandaId: {}, valorTotal: {}, pago: {}, hashCode: {}, identityHashCode: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "abrirPorNumeroMesa",
                contaPai.getId(),
                contaPai.getNumeroConta(),
                comandaSalva.getId(),
                contaPai.getValorTotal(),
                contaPai.getPago(),
                contaPai.hashCode(),
                System.identityHashCode(contaPai),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());

        return new ComandaResponseDTO(comandaSalva, false);
    }

    @Transactional
    public ComandaResponseDTO alterarStatus(UUID id, StatusComanda novoStatus) {
        Comanda comanda = comandaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não localizada com o ID: " + id));
        comanda.setStatus(novoStatus);
        return new ComandaResponseDTO(comandaRepository.save(comanda), true);
    }

    @Transactional
    public ComandaResponseDTO fecharComanda(UUID id) {
        Comanda comanda = comandaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não localizada para encerramento."));

        if (comanda.getStatus() == StatusComanda.FECHADA) {
            throw new BusinessRuleException("Esta comanda já foi encerrada anteriormente.");
        }

        List<Conta> contas = contaRepository.findByComandaId(id);

        if (contas == null || contas.isEmpty()) {
            throw new BusinessRuleException(
                    "Não é possível fechar a comanda sem contas vinculadas."
            );
        }

        boolean possuiContaPendente = contas.stream()
                .anyMatch(conta ->
                        !Boolean.TRUE.equals(conta.getPago())
                );

        if (possuiContaPendente) {
            throw new BusinessRuleException(
                    "Não é possível fechar a comanda enquanto existirem contas com saldo pendente."
            );
        }

        comanda.setStatus(StatusComanda.FECHADA);
        comanda.setFechadaEm(LocalDateTime.now());

        if (comanda.getMesa() != null) {
            Mesa mesa = comanda.getMesa();
            mesa.setStatus(StatusMesa.LIVRE);
            mesaRepository.save(mesa);
        }

        return new ComandaResponseDTO(comandaRepository.save(comanda), true);
    }

    @Transactional(readOnly = true)
    public ComandaResponseDTO buscarPorId(UUID id) {
        Comanda comanda = comandaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comanda não localizada com o ID: " + id));
        return new ComandaResponseDTO(comanda, true);
    }

    @Transactional(readOnly = true)
    public List<ComandaResponseDTO> listarTodasAtivas() {
        // ✅ Busca otimizada com delegação de filtro para o banco
        return comandaRepository.findByStatus(StatusComanda.ABERTA).stream()
                .map(c -> new ComandaResponseDTO(c, true))
                .collect(Collectors.toList());
    }
}
