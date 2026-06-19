package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

@Entity
@Table(name = "subconta")
public class Subconta {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "numero_conta", nullable = false)
    private Integer numeroConta;

    @Column(nullable = false)
    private Boolean pago = false;

    @ManyToOne
    @JoinColumn(name = "comanda_id", nullable = false)
    @JsonIgnore // 🚀 ALTERADO: Evita o loop infinito (nesting depth) no parse do JSON
    private Comanda comanda;

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Integer getNumeroConta() { return numeroConta; }
    public void setNumeroConta(Integer numeroConta) { this.numeroConta = numeroConta; }

    public Boolean getPago() { return pago; }
    public void setPago(Boolean pago) { this.pago = pago; }

    public Comanda getComanda() { return comanda; }
    public void setComanda(Comanda comanda) { this.comanda = comanda; }
}