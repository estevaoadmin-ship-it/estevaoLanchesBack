package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditoria_caixa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditoriaCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario; // Quem fez a auditoria

    @NotBlank
    @Column(nullable = false, length = 50)
    private String acao; // Ex: "CAIXA_ABERTO", "SANGRIA_REALIZADA", "CAIXA_REABERTO"

    @NotNull
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @NotNull
    @Column(name = "caixa_id", nullable = false)
    private UUID caixaId;

    @Column(name = "dados_antes", columnDefinition = "TEXT")
    private String dadosAntes; // Captura em String/JSON do estado anterior

    @Column(name = "dados_depois", columnDefinition = "TEXT")
    private String dadosDepois; // Captura em String/JSON do novo estado
}