package com.paullomaggio.estevaoLanches.enums;

/**
 * Enum expandido para controle absoluto do livro-razão, auditoria de turnos
 * e conciliação de fechamento cego de caixa.
 */
public enum TipoMovimentacao {
    ABERTURA,
    SUPRIMENTO,
    SANGRIA,
    FECHAMENTO,
    QUEBRA_POSITIVA,  // Sobrou dinheiro no fechamento cego
    QUEBRA_NEGATIVA   // Faltou dinheiro no fechamento cego
}