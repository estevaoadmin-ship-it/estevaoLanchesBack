package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrinhoRepository extends JpaRepository<Carrinho, UUID> {
    // Busca o carrinho ativo de um cliente específico
    Optional<Carrinho> findByClienteId(UUID clienteId);
}