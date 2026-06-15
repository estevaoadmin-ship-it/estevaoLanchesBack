package com.paullomaggio.estevaoLanches.enums;

public enum TipoMovimentacao {
    ABERTURA,
    SUPRIMENTO,
    SANGRIA,
    FECHAMENTO,
    QUEBRA_POSITIVA,  // Sobrou dinheiro no fechamento cego
    QUEBRA_NEGATIVA   // Faltou dinheiro no fechamento cego
}