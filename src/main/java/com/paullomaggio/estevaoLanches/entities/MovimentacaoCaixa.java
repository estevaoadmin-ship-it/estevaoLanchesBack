package com.paullomaggio.estevaoLanches.entities;

import com.paullomaggio.estevaoLanches.enums.MotivoMovimentacao;
import com.paullomaggio.estevaoLanches.enums.TipoMovimentacao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimentacao_caixa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MovimentacaoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id", nullable = false)
    private Caixa caixa;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoMovimentacao tipo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoMovimentacao motivo;

    @NotNull
    @PositiveOrZero(message = "O valor da movimentação não pode ser negativo.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(columnDefinition = "TEXT")
    private String observacao; // Detalhamento livre (ex: "Compra de 5kg de tomate")

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario; // Quem realizou a ação

    @NotNull
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    // --- BLOCO DE SEGURANÇA: RASTREABILIDADE DE ESTORNOS ---
    @Column(nullable = false)
    private Boolean cancelada = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelado_por_id")
    private Usuario canceladoPor;

    @Column(name = "data_hora_cancelamento")
    private LocalDateTime dataHoraCancelamento;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;
}