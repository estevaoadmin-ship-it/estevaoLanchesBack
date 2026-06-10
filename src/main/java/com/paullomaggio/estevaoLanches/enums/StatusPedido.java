package com.paullomaggio.estevaoLanches.enums;

public enum StatusPedido {
    RECEBIDO,     // Pedido acabou de chegar do Carrinho/App
    EM_PREPARO,   // Cozinha aceitou e está fazendo o lanche
    PRONTO,       // Lanche na rampa esperando garçom ou motoboy
    EM_ROTA,      // Motoboy saiu para entrega (Apenas para DELIVERY)
    FINALIZADO,   // Entregue ao cliente e pago
    CANCELADO     // Cliente desistiu ou restaurante rejeitou
}