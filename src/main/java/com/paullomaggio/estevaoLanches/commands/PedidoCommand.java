package com.paullomaggio.estevaoLanches.commands;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.Conta;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PedidoCommand {
    private Cliente cliente;
    private Conta conta;
    private TipoPedido tipoPedido;
    private FormaPagamento formaPagamento;
    private String enderecoEntrega;
    private String observacao;
    private String nomeConsumidorBalcao;
    private UUID carrinhoId;
    private Integer numeroMesa;
}