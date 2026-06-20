package com.paullomaggio.estevaoLanches.entities;

import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "mesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private UUID empresaId;

    @Column(nullable = false)
    private UUID filialId;

    @Column(nullable = false, unique = true)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMesa status = StatusMesa.LIVRE;

    /**
     * 🛡️ LIFECYCLE CALLBACK: Injeção Automática de Mocks
     * Antes de disparar o INSERT para o PostgreSQL, se a mesa não tiver empresa/filial,
     * o Spring preenche com os IDs padrões do projeto automaticamente.
     */
    @PrePersist
    public void prePersist() {
        if (this.empresaId == null) {
            // 🎯 Substitua pelo UUID real da empresa padrão que você usa nos seus testes/banco
            this.empresaId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        if (this.filialId == null) {
            // 🎯 Substitua pelo UUID real da filial padrão que você usa nos seus testes/banco
            this.filialId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        }
    }
}