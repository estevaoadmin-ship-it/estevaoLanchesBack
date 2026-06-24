package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paullomaggio.estevaoLanches.enums.StatusEnvioItem;
import com.paullomaggio.estevaoLanches.enums.StatusPagamento;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "item_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    @JsonIgnoreProperties("itens")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    private String observacaoItem;

    @Column(name = "numero_conta", nullable = false)
    private Integer numeroConta = 1;

    @Column(name = "status_pagamento", nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusPagamento statusPagamento = StatusPagamento.ABERTO;

    @Column(name = "status_envio", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private StatusEnvioItem statusEnvio = StatusEnvioItem.AGUARDANDO_ENVIO;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "item_pedido_adicional",
            joinColumns = @JoinColumn(name = "item_pedido_id"),
            inverseJoinColumns = @JoinColumn(name = "adicional_id")
    )
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private List<Adicional> adicionais = new ArrayList<>();

    // 🛡️ Getters e Setters manuais à prova de falhas do compilador ou Lombok
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }

    public Integer getQuantidade() { return quantidade; }
    public void setString(Integer quantidade) { this.quantidade = quantidade; }

    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }

    public String getObservacaoItem() { return observacaoItem; }
    public void setObservacaoItem(String observacaoItem) { this.observacaoItem = observacaoItem; }

    public Integer getNumeroConta() { return numeroConta; }
    public void setNumeroConta(Integer numeroConta) { this.numeroConta = numeroConta; }

    public StatusPagamento getStatusPagamento() { return statusPagamento; }
    public void setStatusPagamento(StatusPagamento statusPagamento) { this.statusPagamento = statusPagamento; }

    public StatusEnvioItem getStatusEnvio() { return statusEnvio; }
    public void setStatusEnvio(StatusEnvioItem statusEnvio) { this.statusEnvio = statusEnvio; }

    public List<Adicional> getAdicionais() { return adicionais; }
    public void setAdicionais(List<Adicional> adicionais) { this.adicionais = adicionais; }
}