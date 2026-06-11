package com.paullomaggio.estevaoLanches.entities;

import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "caixa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Caixa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private LocalDateTime dataHoraAbertura = LocalDateTime.now();

    private LocalDateTime dataHoraFechamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCaixa status = StatusCaixa.ABERTO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorAbertura = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorFechamento;

    // QUEM ABRIU: Relacionamento obrigatório na abertura
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_abertura_id", nullable = false)
    private Usuario usuarioAbertura;

    // QUEM FECHOU: Fica null até o momento do fechamento do turno
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_fechamento_id")
    private Usuario usuarioFechamento;
}