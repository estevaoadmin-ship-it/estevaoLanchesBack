package com.paullomaggio.estevaoLanches.entities;

import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "comanda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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
    private Mesa mesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusComanda status = StatusComanda.ABERTA;

    @Column(nullable = false, updatable = false)
    private LocalDateTime abertaEm = LocalDateTime.now();

    private LocalDateTime fechadaEm;

    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Subconta> subcontas = new ArrayList<>();

    public void setDataHoraAbertura(LocalDateTime now) {
    }
}