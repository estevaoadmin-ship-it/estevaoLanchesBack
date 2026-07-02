package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import com.paullomaggio.estevaoLanches.services.especialistas.PedidoPDVService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GarcomMesaSessaoService {

    private final PedidoPDVService pedidoPDVService;
    private final MesaRepository mesaRepository;
    private final ComandaRepository comandaRepository;

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

                // 🎯 FIX: Passando os 6 argumentos exigidos pelo construtor do seu ItemPedidoPayloadDTO
                List<PedidoMobileRequestDTO.ItemPedidoPayloadDTO> itensParaLegado = contaSync.novosItens().stream()
                        .map(itemNovo -> new PedidoMobileRequestDTO.ItemPedidoPayloadDTO(
                                itemNovo.produtoId(),
                                null, // nome (O seu PedidoPDVService resolve internamente pelo ID)
                                itemNovo.quantidade(),
                                null, // precoCalculado (O seu PedidoPDVService calcula nativamente no backend)
                                itemNovo.observacao(),
                                itemNovo.adicionaisIds()
                        )).toList();

                // 🎯 FIX: Organizada a ordem exata dos parâmetros do seu PedidoMobileRequestDTO mestre
                PedidoMobileRequestDTO loteRequest = new PedidoMobileRequestDTO(
                        request.comandaId(),      // comandaId
                        mesa.getNumero(),         // numeroMesa
                        contaSync.numeroConta(),  // numeroConta
                        null,                     // cliente payload (Opcional no fluxo de inserção de itens)
                        itensParaLegado           // lista de itens
                );

                // Dispara a sua regra de negócio nativa (calcula preço, valida caixa e manda imprimir)
                pedidoPDVService.processarPedidoMobile(loteRequest);
            }
        }

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
                                        true, // Se veio do banco, o item já foi "enviado" (Fica cinza na UI)
                                        adicionaisDTO
                                );
                            }).toList();

                    // Calcula o valor real da subconta somando o total de cada item ativo
                    BigDecimal valorTotalConta = itensDTO.stream()
                            .map(GarcomMesaSessaoResponseDTO.ItemSessaoDTO::valorTotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Mapeia o cliente vinculado à subconta (se houver)
                    GarcomMesaSessaoResponseDTO.ClienteSessaoDTO clienteDTO = null;
                    if (conta.getCliente() != null) {
                        // 🎯 FIX: getTelefone() alterado para getNumero() conforme mapeado na sua Entidade Cliente
                        clienteDTO = new GarcomMesaSessaoResponseDTO.ClienteSessaoDTO(
                                conta.getCliente().getId(),
                                conta.getCliente().getNome(),
                                conta.getCliente().getNumero()
                        );
                    }

                    // Define os status visuais e de estado para o aplicativo
                    boolean isSelecionada = conta.getId().equals(contaSelecionadaId);
                    // 🎯 FIX: StatusConta fantasma alterado para usar seu StatusPagamento oficial
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
}