package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidade que representa a Sessão Mestre de atendimento de uma mesa no salão.
 * Governa diretamente a coleção de Contas (subcontas) geradas para o atendimento.
 */
@Entity
@Table(name = "comanda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Comanda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private UUID empresaId;

    @Column(nullable = false)
    private UUID filialId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "comandas"})
    private Mesa mesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusComanda status = StatusComanda.ABERTA;

    @Column(nullable = false, updatable = false)
    private LocalDateTime abertaEm = LocalDateTime.now();

    private LocalDateTime fechadaEm;

    /**
     * 🎯 ARQUITETURA PURISTA:
     * A comanda governa exclusivamente as Contas.
     * Ela não enxerga os Pedidos diretamente, o acesso se dá navegando pela Conta.
     */
    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("comanda")
    private List<Conta> contas = new ArrayList<>();

    public void setDataHoraAbertura(LocalDateTime now) {
        this.abertaEm = now;
    }
}