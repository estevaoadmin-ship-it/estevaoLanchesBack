package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GarcomMesaSessaoService {

    private final PedidoCoreService coreService;
    private final MesaRepository mesaRepository;
    private final ComandaRepository comandaRepository;
    private final ContaRepository contaRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemComboRepository itemComboRepository;

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
                                null, // nome
                                itemNovo.quantidade(),
                                null, // precoCalculado
                                itemNovo.observacao(),
                                itemNovo.adicionaisIds(),
                                itemNovo.itensCombo() // Propagando o novo campo
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

        entityManager.flush();
        entityManager.clear();

        // 3. Retorna a foto atualizada do banco para limpar os itens verdes no Mobile
        return obterSessao(mesaId);
    }

    /**
     * 📤 LEITURA: Monta toda a estrutura da sessão de atendimento para o Mobile.
     */
    @Transactional(readOnly = true)
    public GarcomMesaSessaoResponseDTO obterSessao(UUID mesaId) {
        // 1. Localiza a Mesa
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new ResourceNotFoundException("Mesa não encontrada no sistema."));

        // 2. Localiza a Comanda Ativa daquela Mesa
        Comanda comanda = comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma comanda aberta localizada para a mesa número " + mesa.getNumero()));

        return montarSessao(mesa, comanda);
    }

    /**
     * 📤 LEITURA EM LOTE: Retorna as sessões de todas as comandas abertas.
     */
    @Transactional(readOnly = true)
    public List<GarcomMesaSessaoResponseDTO> obterSessoesAtivas() {
        return comandaRepository.findByStatus(StatusComanda.ABERTA).stream()
                .map(comanda -> montarSessao(comanda.getMesa(), comanda))
                .toList();
    }

    private GarcomMesaSessaoResponseDTO montarSessao(Mesa mesa, Comanda comanda) {
        // 3. Define a subconta pré-selecionada na UI (Regra: A primeira subconta aberta/não paga)
        UUID contaSelecionadaId = comanda.getContas().stream()
                .filter(c -> !c.getPago())
                .map(Conta::getId)
                .findFirst()
                .orElse(comanda.getContas().isEmpty() ? null : comanda.getContas().get(0).getId());

        // 4. Mapeia a árvore de Contas -> Pedidos -> Itens -> Adicionais
        List<GarcomMesaSessaoResponseDTO.ContaSessaoDTO> contasDTO = comanda.getContas().stream()
                .map(conta -> {

                    // Coleta e achata todos os itens de todos os pedidos ativos (não cancelados) da subconta
                    List<ItemPedido> todosItens = conta.getPedidos().stream()
                            .filter(pedido -> pedido.getStatus() != StatusPedido.CANCELADO)
                            .flatMap(pedido -> pedido.getItens().stream())
                            .toList();
                    
                    // Collect all itemPedido IDs for batch fetching of ItemCombo
                    List<UUID> itemPedidoIds = todosItens.stream()
                            .map(ItemPedido::getId)
                            .toList();
                    
                    // Fetch all ItemCombo for these itemPedido IDs in a single query
                    List<ItemCombo> todosItemCombo = itemPedidoIds.isEmpty()
                            ? List.of()
                            : itemComboRepository.findByItemPedidoIdIn(itemPedidoIds);
                    
                    // Group ItemCombo by itemPedidoId for efficient lookup
                    Map<UUID, List<ItemCombo>> itemComboPorItemPedido = todosItemCombo.stream()
                            .collect(Collectors.groupingBy(itemCombo -> itemCombo.getItemPedido().getId()));
                    
                    List<GarcomMesaSessaoResponseDTO.ItemSessaoDTO> itensDTO = todosItens.stream()
                            .map(item -> {
                                // Mapeia os adicionais do item
                                List<GarcomMesaSessaoResponseDTO.AdicionalDTO> adicionaisDTO = item.getAdicionais().stream()
                                        .map(a -> new GarcomMesaSessaoResponseDTO.AdicionalDTO(a.getId(), a.getNome(), a.getPreco()))
                                        .toList();

                                // O preço unitário base do cardápio
                                BigDecimal valorUnitarioBase = item.getProduto().getPreco();
                                // O valor total calculado do item (quantidade * precoUnitario acumulado com adicionais)
                                BigDecimal valorTotalItem = item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));

                                // Get ItemCombo for this specific itemPedido
                                List<ItemCombo> itemComboParaEsteItem = itemComboPorItemPedido.getOrDefault(item.getId(), List.of());
                                
                                // Convert ItemCombo entities to DTOs
                                List<ItemComboResponseDTO> itensComboDTO = itemComboParaEsteItem.stream()
                                        .map(ItemComboResponseDTO::new)
                                        .toList();

                                return new GarcomMesaSessaoResponseDTO.ItemSessaoDTO(
                                        item.getId(),
                                        item.getPedido().getId(),
                                        item.getPedido().getNumeroPedido(),
                                        item.getPedido().getStatus(),
                                        item.getPedido().getStatusFinanceiro(),
                                        item.getPedido().getTipo(),
                                        item.getProduto().getId(),
                                        item.getProduto().getNome(),
                                        item.getQuantidade(),
                                        valorUnitarioBase,
                                        valorTotalItem,
                                        item.getObservacaoItem(),
                                        item.getProduto().getPrecisaPreparo(),
                                        true,
                                        adicionaisDTO,
                                        itensComboDTO // NEW: Add the historical ItemCombo data
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
                    StatusPagamento statusConta;
                    if (valorTotalConta.compareTo(BigDecimal.ZERO) == 0) {
                        statusConta = StatusPagamento.PAGO;
                    } else {
                        statusConta = conta.getPago() ? StatusPagamento.PAGO : StatusPagamento.ABERTO;
                    }

                    List<PagamentoSessaoDTO> pagamentos = conta.getPagamentos().stream()
                            .map(p -> new PagamentoSessaoDTO(
                                    p.getId(),
                                    p.getFormaPagamento(),
                                    p.getValorPago(),
                                    p.getDataHora(),
                                    p.getUsuarioResponsavel() // CORREÇÃO APLICADA AQUI
                            )).toList();

                    return new GarcomMesaSessaoResponseDTO.ContaSessaoDTO(
                            conta.getId(),
                            conta.getNumeroConta(),
                            statusConta,
                            valorTotalConta,
                            isSelecionada,
                            clienteDTO,
                            itensDTO,
                            pagamentos
                    );
                }).toList();

        // 5. Consolida e retorna o DTO Agregador pronto para o JSON
        return new GarcomMesaSessaoResponseDTO(
                mesa.getId(),
                mesa.getNumero(),
                mesa.getStatus(),
                comanda.getId(),
                comanda.getStatus(),
                comanda.getAbertaEm(),
                contaSelecionadaId,
                contasDTO
        );
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

        return obterSessao(conta.getComanda().getMesa().getId());
    }
}