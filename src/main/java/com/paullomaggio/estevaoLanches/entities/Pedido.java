package com.paullomaggio.estevaoLanches.entities;

import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;

@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    // Código curto e amigável para o cliente (Ex: "A4F2" ou "1045")
    @Column(nullable = false, unique = true, length = 10)
    private String numeroPedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPedido tipo; // MESA, DELIVERY, RETIRADA

    private Integer numeroMesa;
    private String enderecoEntrega;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 255)
    private String observacaoGeral;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();

    // Gera um código alfanumérico aleatório de 5 caracteres antes de salvar
    @PrePersist
    public void gerarNumeroPedido() {
        if (this.numeroPedido == null) {
            String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder codigo = new StringBuilder();
            Random rnd = new Random();
            while (codigo.length() < 5) {
                int index = (int) (rnd.nextFloat() * caracteres.length());
                codigo.append(caracteres.charAt(index));
            }
            this.numeroPedido = codigo.toString();
        }
    }
}