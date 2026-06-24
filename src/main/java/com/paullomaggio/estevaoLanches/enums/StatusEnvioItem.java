package com.paullomaggio.estevaoLanches.enums;

/**
 * Enum para triagem de sincronismo visual do aplicativo mobile do salão.
 */
public enum StatusEnvioItem {
    AGUARDANDO_ENVIO, // Item em rascunho local na sacola do garçom
    ENVIADO           // Item transmitido, fixado no caixa e impresso na cozinha (Fica cinza fosco)
}