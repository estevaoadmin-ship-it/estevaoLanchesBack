package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO exclusivo de resposta da consulta de Histórico de Caixa.
 * <p>
 * Todos os valores financeiros já chegam calculados pelo Backend (única fonte da
 * verdade), de modo que o Frontend apenas monta a tabela sem realizar qualquer cálculo.
 * A montagem reutiliza integralmente a mesma rotina financeira usada pelo resumo do
 * turno (faturamento por forma, estornos, sangrias, suprimentos e saldo esperado).
 */
@Schema(description = "Turno de caixa consolidado para a consulta de Histórico de Caixa")
public record CaixaHistoricoResponseDTO(
        @Schema(description = "Identificador único do turno de caixa", format = "uuid",
                example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Status do turno de caixa", allowableValues = {"ABERTO", "FECHADO"},
                example = "FECHADO")
        StatusCaixa status,

        @Schema(description = "Data e hora de abertura do turno", example = "2026-06-30T09:00:00")
        LocalDateTime dataHoraAbertura,

        @Schema(description = "Data e hora de fechamento do turno", example = "2026-06-30T19:00:00")
        LocalDateTime dataHoraFechamento,

        @Schema(description = "Nome do usuário responsável pela abertura", example = "ESTEVAO ADMINISTRADOR")
        String nomeUsuarioAbertura,

        @Schema(description = "Nome do usuário responsável pelo fechamento", example = "ESTEVAO ADMINISTRADOR")
        String nomeUsuarioFechamento,

        @Schema(description = "Valor de abertura do caixa", example = "150.00")
        BigDecimal valorAbertura,

        @Schema(description = "Valor contado fisicamente no fechamento do caixa", example = "1270.00")
        BigDecimal valorFechamento,

        @Schema(description = "Faturamento via dinheiro do turno (líquido de estornos)", example = "90.00")
        BigDecimal faturamentoDinheiro,

        @Schema(description = "Faturamento via PIX do turno (líquido de estornos)", example = "180.00")
        BigDecimal faturamentoPix,

        @Schema(description = "Faturamento via cartão de crédito do turno (líquido de estornos)", example = "50.00")
        BigDecimal faturamentoCredito,

        @Schema(description = "Faturamento via cartão de débito do turno (líquido de estornos)", example = "0.00")
        BigDecimal faturamentoDebito,

        @Schema(description = "Faturamento total do turno (líquido de estornos)", example = "320.00")
        BigDecimal faturamentoTotal,

        @Schema(description = "Valor total das sangrias do turno (não estornadas)", example = "20.00")
        BigDecimal totalSangrias,

        @Schema(description = "Quantidade de sangrias do turno (não estornadas)", example = "1")
        long quantidadeSangrias,

        @Schema(description = "Valor total dos suprimentos do turno (não estornados)", example = "50.00")
        BigDecimal totalSuprimentos,

        @Schema(description = "Quantidade de suprimentos do turno (não estornados)", example = "1")
        long quantidadeSuprimentos,

        @Schema(description = "Saldo esperado na gaveta (abertura + faturamento dinheiro + suprimentos - sangrias)",
                example = "270.00")
        BigDecimal saldoEsperado,

        @Schema(description = "Diferença de caixa (valorFechamento - saldoEsperado)", example = "0.00")
        BigDecimal diferencaCaixa,

        @Schema(description = "Observação / justificativa registrada no fechamento", example = "Diferença de troco")
        String observacaoFechamento,

        @Schema(description = "Lista de sangrias individuais do turno (não estornadas)")
        List<CaixaSangriaItemDTO> sangrias
) {}
