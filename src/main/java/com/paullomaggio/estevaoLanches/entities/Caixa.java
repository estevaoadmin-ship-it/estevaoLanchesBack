package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@AllArgsConstructor // 🎯 Trava o construtor de 10 parâmetros para os Mocks de teste
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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

    private String justificativaDiferenca; // Campo novo de fechamento cego
    private String motivoReabertura;       // Campo novo de auditoria

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_abertura_id", nullable = false)
    private Usuario usuarioAbertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_fechamento_id")
    private Usuario usuarioFechamento;
}