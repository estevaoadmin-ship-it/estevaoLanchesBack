package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    // 🚀 NOVO: Utilizado pela Captura Automática do Mobile para identificar o cliente pelo WhatsApp
    Optional<Cliente> findByNumero(String numero);

    // =========================================================================
    // 🖥️ MÉTODOS ORIGINAIS DO PDV WEB (MANTIDOS 100% INTACTOS)
    // =========================================================================
    Optional<Cliente> findByCpf(String cpf);

    Optional<Cliente> findByEmail(String email);

    List<Cliente> findByNomeContainingIgnoreCase(String nome);

    boolean existsByCpfAndIdNot(String cpf, UUID id);

    boolean existsByEmailAndIdNot(String email, UUID id);
}