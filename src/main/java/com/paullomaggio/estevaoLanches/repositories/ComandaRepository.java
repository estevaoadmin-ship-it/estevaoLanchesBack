package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComandaRepository extends JpaRepository<Comanda, UUID> {

    // Busca a comanda ativa de uma mesa específica
    Optional<Comanda> findByMesaNumeroAndStatus(Integer numeroMesa, StatusComanda status);

    // Delega o filtro para o banco de dados
    List<Comanda> findByStatus(StatusComanda status);
}