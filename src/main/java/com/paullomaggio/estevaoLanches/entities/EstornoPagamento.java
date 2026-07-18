package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "estorno_pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EstornoPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id", nullable = false)
    private Caixa caixa;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorEstornado;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @Column(nullable = false)
    private String usuarioResponsavel;

    @PrePersist
    @PreUpdate
    public void validar() {
        if (pagamento == null) {
            throw new IllegalArgumentException("O pagamento não pode ser nulo.");
        }
        if (caixa == null) {
            throw new IllegalArgumentException("O caixa não pode ser nulo.");
        }
        if (valorEstornado == null) {
            throw new IllegalArgumentException("O valor estornado não pode ser nulo.");
        }
        if (valorEstornado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor estornado deve ser maior que zero.");
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("O motivo não pode ser nulo ou vazio.");
        }
        if (dataHora == null) {
            throw new IllegalArgumentException("A data e hora não podem ser nulas.");
        }
        if (usuarioResponsavel == null || usuarioResponsavel.trim().isEmpty()) {
            throw new IllegalArgumentException("O usuário responsável não pode ser nulo ou vazio.");
        }
    }

    // Getters e Setters manuais
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Pagamento getPagamento() {
        return pagamento;
    }

    public void setPagamento(Pagamento pagamento) {
        this.pagamento = pagamento;
    }

    public Caixa getCaixa() {
        return caixa;
    }

    public void setCaixa(Caixa caixa) {
        this.caixa = caixa;
    }

    public BigDecimal getValorEstornado() {
        return valorEstornado;
    }

    public void setValorEstornado(BigDecimal valorEstornado) {
        this.valorEstornado = valorEstornado;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getUsuarioResponsavel() {
        return usuarioResponsavel;
    }

    public void setUsuarioResponsavel(String usuarioResponsavel) {
        this.usuarioResponsavel = usuarioResponsavel;
    }
}