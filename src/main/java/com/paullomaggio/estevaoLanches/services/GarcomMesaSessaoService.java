package com.paullomaggio.estevaoLanches.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate; // Importar Hibernate para isInitialized
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GarcomMesaSessaoService {

    private static final Logger log = LoggerFactory.getLogger(GarcomMesaSessaoService.class);
    private final PedidoCoreService coreService;
    private final MesaRepository mesaRepository;
    private final ComandaRepository comandaRepository;
    private final ContaRepository contaRepository; // Inject ContaRepository
    private final PedidoRepository pedidoRepository; // Inject PedidoRepository

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 📥 ESCRITA (SYNC): Recebe os itens novos (verdes) do Mobile e persiste no banco.
     */
    @Transactional
    public GarcomMesaSessaoResponseDTO sincronizarSessao(UUID mesaId, GarcomMesaSessaoRequestDTO request) {

        // 1. Busca a mesa no banco para garantir o número correto
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada no sistema."));

        // 2. Processa os novos itens de cada subconta enviada pelo Mobile
        for (var contaSync : request.contas()) {
            if (contaSync.novosItens() != null && !contaSync.novosItens().isEmpty()) {

                List<PedidoMobileRequestDTO.ItemPedidoPayloadDTO> itensParaLegado = contaSync.novosItens().stream()
                        .map(itemNovo -> new PedidoMobileRequestDTO.ItemPedidoPayloadDTO(
                                itemNovo.produtoId(),
                                null,
                                itemNovo.quantidade(),
                                null,
                                itemNovo.observacao(),
                                itemNovo.adicionaisIds()
                        )).toList();

                // Organizada a ordem exata dos parâmetros do seu PedidoMobileRequestDTO mestre
                PedidoMobileRequestDTO loteRequest = new PedidoMobileRequestDTO(
                        request.comandaId(),
                        mesa.getNumero(),
                        contaSync.numeroConta(),
                        null,
                        itensParaLegado
                );

                // 🎯 CORREÇÃO: Roteamento para a infraestrutura correta da aplicação
                coreService.processarPedidoMobile(loteRequest);
            }
        }

        // 3. Retorna a foto atualizada do banco para limpar os itens verdes no Mobile
        GarcomMesaSessaoResponseDTO dto = obterSessao(mesaId);

        // AUDITORIA 2
        log.info("=============================");
        log.info("AUDITORIA 2 - sincronizarSessao");
        log.info("=============================");
        log.info("Quantidade de contas: {}", dto.contas().size());
        int totalPedidos = dto.contas().stream().mapToInt(c -> c.itens().size()).sum(); // Assuming items are flattened from pedidos
        log.info("Quantidade de itens: {}", totalPedidos);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String json = mapper.writeValueAsString(dto);
            log.info("JSON completo do DTO retornado por sincronizarSessao:\n{}", json);

            boolean hasEmptyItems = dto.contas().stream().anyMatch(c -> c.itens().isEmpty());
            if (hasEmptyItems) {
                log.info("Existe conta com lista de itens vazia: {}", hasEmptyItems);
            } else {
                log.info("Todas as contas possuem itens.");
            }

        } catch (Exception e) {
            log.error("Erro ao serializar DTO em sincronizarSessao", e);
        }
        log.info("=============================");

        return dto;
    }

    /**
     * 📤 LEITURA: Monta toda a estrutura da sessão de atendimento para o Mobile.
     */
    @Transactional(readOnly = true)
    public GarcomMesaSessaoResponseDTO obterSessao(UUID mesaId) {
        // 1. Localiza a Mesa
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada no sistema."));

        // PASSO 2
        log.info("================================================");
        log.info("AUDITORIA PERSISTENCE CONTEXT");
        log.info("================================================");
        log.info("EntityManager Hash: {}", System.identityHashCode(entityManager));
        log.info("Mesa Hash: {}", System.identityHashCode(mesa));
        log.info("Mesa Gerenciada: {}", entityManager.contains(mesa));
        log.info("================================================");

        // 2. Localiza a Comanda Ativa daquela Mesa
        Comanda comanda = comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma comanda aberta localizada para a mesa número " + mesa.getNumero()));

        // PASSO 3
        log.info("=========== COMANDA ===========");
        log.info("Comanda ID: {}", comanda.getId());
        log.info("Comanda Hash: {}", System.identityHashCode(comanda));
        log.info("Comanda Gerenciada: {}", entityManager.contains(comanda));
        log.info("================================");

        // PASSO 4
        log.info("Quantidade de contas: {}", comanda.getContas().size());

        // 3. Define a subconta pré-selecionada na UI (Regra: A primeira subconta aberta/não paga)
        UUID contaSelecionadaId = comanda.getContas().stream()
                .filter(c -> !c.getPago())
                .map(Conta::getId)
                .findFirst()
                .orElse(comanda.getContas().isEmpty() ? null : comanda.getContas().get(0).getId());

        // AUDITORIA FINAL - obterSessao()
        log.info("==========================================");
        log.info("AUDITORIA FINAL - obterSessao()");
        log.info("==========================================");

        for (Conta conta : comanda.getContas()) {
            // PASSO 5 - Início do laço da Conta
            log.info("--------------------------------");
            log.info("Conta Número: {}", conta.getNumeroConta());
            log.info("Conta UUID: {}", conta.getId());
            log.info("Conta Hash: {}", System.identityHashCode(conta));
            log.info("Conta Gerenciada: {}", entityManager.contains(conta));
            log.info("Pedidos Inicializados: {}", Hibernate.isInitialized(conta.getPedidos()));

            log.info("Conta {}", conta.getNumeroConta());
            log.info("Pedidos: {}", conta.getPedidos().size());
            Hibernate.initialize(conta.getPedidos()); // Ensure pedidos are initialized
            // PASSO 5 - Após initialize
            log.info("Pedidos após initialize: {}", conta.getPedidos().size());
            log.info("Pedidos Inicializados Agora: {}", Hibernate.isInitialized(conta.getPedidos()));

            // CÓDIGO DE AUDITORIA COMPARATIVA (PASSO 2 DA NOVA MISSÃO)
            List<Pedido> pedidosBanco =
                    pedidoRepository.findByContaIdIn(List.of(conta.getId()));

            log.info("========== AUDITORIA CONTA ==========");
            log.info("Conta ID...............: {}", conta.getId());
            log.info("Número Conta...........: {}", conta.getNumeroConta());
            log.info("Pedidos via JPA........: {}", conta.getPedidos().size());
            log.info("Pedidos via Repository.: {}", pedidosBanco.size());

            for (Pedido pedido : pedidosBanco) {
                Hibernate.initialize(pedido.getItens());
                log.info("Pedido Banco -> id={}, itens={}",
                        pedido.getId(),
                        pedido.getItens().size());
            }

            for (Pedido pedido : conta.getPedidos()) {
                Hibernate.initialize(pedido.getItens());
                log.info("Pedido JPA -> id={}, itens={}",
                        pedido.getId(),
                        pedido.getItens().size());
            }
            log.info("=======================================");


            for (Pedido pedido : conta.getPedidos()) {
                // PASSO 6 - Início do laço do Pedido
                log.info("Pedido ID: {}", pedido.getId());
                log.info("Pedido Hash: {}", System.identityHashCode(pedido));
                log.info("Pedido Gerenciado: {}", entityManager.contains(pedido));
                log.info("Itens Inicializados: {}", Hibernate.isInitialized(pedido.getItens()));

                log.info("Pedido {}", pedido.getId());
                log.info("Itens: {}", pedido.getItens().size());
                Hibernate.initialize(pedido.getItens()); // Ensure items are initialized
                // PASSO 6 - Após initialize
                log.info("Itens após initialize: {}", pedido.getItens().size());
                log.info("Itens Inicializados Agora: {}", Hibernate.isInitialized(pedido.getItens()));
            }
        }
        log.info("==========================================");

        // ================================================
        // AUDITORIA COMANDA.CONTAS - ANTES DO MAPPER
        // ================================================
        log.info("================================================");
        log.info("AUDITORIA COMANDA.CONTAS - ANTES DO MAPPER");
        log.info("================================================");
        log.info("Comanda ID: {}", comanda.getId());
        log.info("Hibernate.isInitialized(comanda.getContas()): {}", Hibernate.isInitialized(comanda.getContas()));
        log.info("Quantidade de contas na comanda: {}", comanda.getContas().size());

        for (Conta conta : comanda.getContas()) {
            log.info("================================");
            log.info("CONTA " + conta.getNumeroConta());
            log.info("ID: " + conta.getId());
            Hibernate.initialize(conta.getPedidos()); // Ensure pedidos are initialized
            log.info("Pedidos: " + conta.getPedidos().size());

            for (Pedido pedido : conta.getPedidos()) {
                log.info("--------------------------------");
                log.info("Pedido: " + pedido.getId());
                log.info("Status: " + pedido.getStatus());
                Hibernate.initialize(pedido.getItens()); // Ensure items are initialized
                log.info("Itens: " + pedido.getItens().size());

                for (ItemPedido item : pedido.getItens()) {
                    log.info(
                            "Item: "
                                    + item.getId()
                                    + " | Produto: "
                                    + item.getProduto().getNome()
                                    + " | Qtde: "
                                    + item.getQuantidade()
                    );
                }
            }
            log.info("================================");
        }
        log.info("================================================");


        // 4. Mapeia a árvore de Contas -> Pedidos -> Itens -> Adicionais
        List<GarcomMesaSessaoResponseDTO.ContaSessaoDTO> contasDTO = comanda.getContas().stream()
                .map(conta -> {
                    // AUDITORIA 7
                    log.info("=============================");
                    log.info("AUDITORIA 7 - GarcomMesaSessaoService.obterSessao() - Conta carregada");
                    log.info("=============================");
                    log.info("Conta UUID: {}", conta.getId());
                    log.info("Conta Número: {}", conta.getNumeroConta());
                    log.info("conta.getPedidos().size(): {}", conta.getPedidos().size());
                    if (!conta.getPedidos().isEmpty()) {
                        conta.getPedidos().forEach(p -> {
                            log.info("  - Pedido ID: {}", p.getId());
                            log.info("    Itens no Pedido: {}", p.getItens().size());
                            p.getItens().forEach(item -> log.info("      - Item ID: {}, Produto: {}, Quantidade: {}", item.getId(), item.getProduto().getNome(), item.getQuantidade()));
                        });
                    } else {
                        log.info("  Nenhum pedido encontrado na Conta carregada em obterSessao.");
                    }
                    log.info("=============================");

                    // Coleta e achata todos os itens de todos os pedidos ativos (não cancelados) da subconta
                    List<GarcomMesaSessaoResponseDTO.ItemSessaoDTO> itensDTO = conta.getPedidos().stream()
                            .filter(pedido -> pedido.getStatus() != StatusPedido.CANCELADO)
                            .flatMap(pedido -> pedido.getItens().stream())
                            .map(item -> {

                                // Mapeia os adicionais do item
                                List<GarcomMesaSessaoResponseDTO.AdicionalDTO> adicionaisDTO = item.getAdicionais().stream()
                                        .map(a -> new GarcomMesaSessaoResponseDTO.AdicionalDTO(a.getId(), a.getNome(), a.getPreco()))
                                        .toList();

                                // O preço unitário base do cardápio
                                BigDecimal valorUnitarioBase = item.getProduto().getPreco();
                                // O valor total calculado do item (quantidade * precoUnitario acumulado com adicionais)
                                BigDecimal valorTotalItem = item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));

                                return new GarcomMesaSessaoResponseDTO.ItemSessaoDTO(
                                        item.getId(),
                                        item.getProduto().getId(),
                                        item.getProduto().getNome(),
                                        item.getQuantidade(),
                                        valorUnitarioBase,
                                        valorTotalItem,
                                        item.getObservacaoItem(),
                                        item.getProduto().getPrecisaPreparo(),
                                        true,
                                        adicionaisDTO
                                );
                            }).toList();

                    // Calcula o valor real da subconta somando o total de cada item ativo
                    BigDecimal valorTotalConta = itensDTO.stream()
                            .map(GarcomMesaSessaoResponseDTO.ItemSessaoDTO::valorTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // 🎯 CORREÇÃO: Alinhamento de Domínio. Mesa não possui entidade Cliente persistida.
                    // Os dados são extraídos de forma segura dos campos do responsável da própria subconta.
                    GarcomMesaSessaoResponseDTO.ClienteSessaoDTO clienteDTO = null;
                    if (conta.getNomeResponsavel() != null && !conta.getNomeResponsavel().isBlank()) {
                        clienteDTO = new GarcomMesaSessaoResponseDTO.ClienteSessaoDTO(
                                conta.getId(),
                                conta.getNomeResponsavel(),
                                conta.getTelefoneResponsavel()
                        );
                    }

                    // Define os status visuais e de estado para o aplicativo
                    boolean isSelecionada = conta.getId().equals(contaSelecionadaId);
                    StatusPagamento statusConta = conta.getPago() ? StatusPagamento.PAGO : StatusPagamento.ABERTO;

                    return new GarcomMesaSessaoResponseDTO.ContaSessaoDTO(
                            conta.getId(),
                            conta.getNumeroConta(),
                            statusConta,
                            valorTotalConta,
                            isSelecionada,
                            clienteDTO,
                            itensDTO
                    );
                }).toList();

        // ================================================
        // AUDITORIA COMANDA.CONTAS - DEPOIS DO MAPPER
        // ================================================
        log.info("================================================");
        log.info("AUDITORIA COMANDA.CONTAS - DEPOIS DO MAPPER");
        log.info("================================================");
        log.info("Quantidade de contasDTO: {}", contasDTO.size());

        for (GarcomMesaSessaoResponseDTO.ContaSessaoDTO contaDTO : contasDTO) {
            log.info("  Conta DTO UUID: {}", contaDTO.id());
            log.info("  Número: {}", contaDTO.numeroConta());
            log.info("  Quantidade de itens: {}", contaDTO.itens().size());
            log.info("  Cliente: {}", contaDTO.cliente() != null ? contaDTO.cliente().nome() : "N/A");
            log.info("  isSelecionada: {}", contaDTO.isSelecionada());
        }
        log.info("================================================");


        // 5. Consolida e retorna o DTO Agregador pronto para o JSON
        GarcomMesaSessaoResponseDTO dto = new GarcomMesaSessaoResponseDTO(
                mesa.getId(),
                mesa.getNumero(),
                mesa.getStatus(),
                comanda.getId(),
                comanda.getStatus(),
                comanda.getAbertaEm(),
                contaSelecionadaId,
                contasDTO
        );

        // AUDITORIA 1 e 4
        log.info("=============================");
        log.info("AUDITORIA 1 e 4 - obterSessao");
        log.info("=============================");
        log.info("Mesa: {}", dto.numeroMesa());
        log.info("UUID da Mesa: {}", dto.mesaId());
        log.info("Quantidade de contas: {}", dto.contas().size());

        for (int i = 0; i < dto.contas().size(); i++) {
            GarcomMesaSessaoResponseDTO.ContaSessaoDTO conta = dto.contas().get(i);
            log.info("Conta {}:", i + 1);
            log.info("  UUID: {}", conta.id());
            log.info("  Número: {}", conta.numeroConta());
            if (conta.cliente() != null) {
                log.info("  Cliente: {}", conta.cliente().nome());
            } else {
                log.info("  Cliente: N/A");
            }
            log.info("  Quantidade de pedidos (itens): {}", conta.itens().size()); // Assuming items are flattened from pedidos

            // AUDITORIA 8
            log.info("AUDITORIA 8 - Detalhes da Conta {}", i + 1);
            log.info("  Conta UUID: {}", conta.id());
            log.info("  Número da Conta: {}", conta.numeroConta());
            log.info("  Quantidade de itens: {}", conta.itens().size());
            log.info("  Lista dos itens:");
            conta.itens().forEach(item -> log.info("    - {} ({}x)", item.nomeProduto(), item.quantidade()));
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String json = mapper.writeValueAsString(dto);
            log.info("JSON completo do DTO retornado por obterSessao:\n{}", json);
        } catch (Exception e) {
            log.error("Erro ao serializar DTO em obterSessao", e);
        }
        log.info("=============================");

        // ================= AUDITORIA BACKEND =================
        log.info("================= AUDITORIA BACKEND =================\n");
        for (Conta conta : comanda.getContas()) {
            log.info("Conta: {}", conta.getNumeroConta());
            log.info("UUID: {}", conta.getId());

            Hibernate.initialize(conta.getPedidos()); // Garante que os pedidos da conta estão carregados
            List<ItemPedido> todosItensDaConta = conta.getPedidos().stream()
                    .flatMap(pedido -> {
                        Hibernate.initialize(pedido.getItens()); // Garante que os itens de cada pedido estão carregados
                        return pedido.getItens().stream();
                    })
                    .collect(Collectors.toList());

            log.info("Quantidade de itens: {}", todosItensDaConta.size());

            if (!todosItensDaConta.isEmpty()) {
                for (ItemPedido item : todosItensDaConta) {
                    log.info("- {}", item.getProduto().getNome());
                }
            }
            log.info("\n----------------------------------------\n");
        }
        log.info("=====================================================\n");
        // ================= AUDITORIA BACKEND =================

        return dto;
    }

    @Transactional
    public GarcomMesaSessaoResponseDTO salvarResponsavel(UUID contaId, SalvarResponsavelRequestDTO dto) {
        Conta conta = contaRepository.findById(contaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada com o ID: " + contaId));

        if (conta.hasRealResponsavel()) {
            throw new BusinessRuleException("Já existe um responsável cadastrado para esta conta.");
        }

        conta.setNomeResponsavel(dto.nome());
        conta.setTelefoneResponsavel(dto.telefone());
        contaRepository.save(conta);

        // Return updated session DTO
        return obterSessao(conta.getComanda().getMesa().getId());
    }
}