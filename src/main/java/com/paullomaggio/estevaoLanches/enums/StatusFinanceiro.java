package com.paullomaggio.estevaoLanches.enums;

public enum StatusFinanceiro {
    AGUARDANDO_PAGAMENTO, // Cliente consumindo na mesa ou motoboy na rua
    PAGO,                 // Dinheiro na gaveta ou Pix na conta
    ESTORNADO,            // Devolvemos o dinheiro
    CANCELADO             // Pedido cancelado antes de pagar
}