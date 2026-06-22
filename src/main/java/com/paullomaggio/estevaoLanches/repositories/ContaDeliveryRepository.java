package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaDeliveryRepository extends JpaRepository<ContaDelivery, UUID> {
    Optional<ContaDelivery> findByEmail(String email);
    boolean existsByEmail(String email);
}