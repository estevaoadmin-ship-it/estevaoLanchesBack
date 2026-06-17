package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fila_impressao")
@Getter @Setter
public class FilaImpressao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    private DestinoImpressao destino; // COZINHA, BAR, RECIBO_CLIENTE

    @Enumerated(EnumType.STRING)
    private StatusImpressao status = StatusImpressao.PENDENTE;

    private Integer tentativas = 0;
    private LocalDateTime ultimaTentativa;
    private String logErro;
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime impressoEm;

    public enum StatusImpressao { PENDENTE, PROCESSANDO, IMPRESSO, ERRO }
    public enum DestinoImpressao { COZINHA, BAR, RECIBO_CLIENTE }
}