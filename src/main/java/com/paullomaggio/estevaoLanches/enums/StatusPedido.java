package com.paullomaggio.estevaoLanches.enums;

public enum StatusPedido {
    RECEBIDO,     // Pedido acabou de chegar do Carrinho/App/PDV
    EM_PREPARO,   // Cozinha aceitou e está a fazer o lanche
    PRONTO,       // Lanche na rampa à espera do garçom ou motoboy
    EM_ROTA,      // Motoboy saiu para entrega (Apenas para DELIVERY)
    SERVIDO,      // Lanche foi entregue na mesa do cliente (Apenas para MESA)
    FINALIZADO,   // Ciclo operacional totalmente encerrado (Mesa limpa / Entrega concluída)
    CANCELADO     // Cliente desistiu ou restaurante rejeitou
}