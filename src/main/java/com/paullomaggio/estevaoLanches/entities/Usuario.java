package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false, length = 255)
    private String role;

    @Column(nullable = false)
    private boolean ativo;

    // =========================================================================
    // IMPLEMENTAÇÃO DOS MÉTODOS DA INTERFACE USERDETAILS (SPRING SECURITY)
    // =========================================================================

    /**
     * Mapeia o perfil de acesso armazenado no banco de dados para o padrão de
     * autoridades do Spring Security, aplicando o prefixo obrigatório "ROLE_".
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role));
    }

    /**
     * Retorna a credencial de segurança criptografada do usuário.
     */
    @Override
    public String getPassword() {
        return this.senha;
    }

    /**
     * Define o atributo email como o identificador exclusivo de autenticação
     * para o processo de login na aplicação.
     */
    @Override
    public String getUsername() {
        return this.email;
    }

    /**
     * Indica se a conta do usuário expirou. Retorna true para sinalizar validade permanente.
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indica se o usuário está bloqueado. Retorna true para sinalizar conta desbloqueada.
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Indica se as credenciais de acesso expiraram. Retorna true para sinalizar validade ativa.
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Vincula o controle de permissão de acesso do Spring Security com o status
     * logico do campo ativo persistido no banco de dados.
     */
    @Override
    public boolean isEnabled() {
        return this.ativo;
    }
}