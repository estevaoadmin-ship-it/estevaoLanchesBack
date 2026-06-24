package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, UUID> {

    Optional<Mesa> findByNumero(Integer numero);

    /**
     * 🎯 Método estendido para filtragem de painéis do salão no app mobile.
     */
    List<Mesa> findByStatus(StatusMesa status);
}