package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    // Busca um usuário pelo e-mail (essencial para o futuro carregamento do Spring Security/Login)
    Optional<Usuario> findByEmail(String email);

    // Validador rápido de existência para evitar consultas pesadas no banco
    boolean existsByEmail(String email);

    // Lista apenas os funcionários que estão trabalhando ativamente no momento
    List<Usuario> findByAtivoTrue();
}