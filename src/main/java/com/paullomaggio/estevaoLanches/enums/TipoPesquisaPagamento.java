package com.paullomaggio.estevaoLanches.enums;

/**
 * Enum que classifica o domínio de origem da pesquisa de pagamentos.
 * Cada valor direciona a consulta para o método especializado correto no Repository.
 */
public enum TipoPesquisaPagamento {
    CLIENTE,
    MESA,
    PEDIDO
}
