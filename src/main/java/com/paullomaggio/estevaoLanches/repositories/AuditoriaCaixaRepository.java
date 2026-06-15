package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.AuditoriaCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AuditoriaCaixaRepository extends JpaRepository<AuditoriaCaixa, UUID> {
    // Repositório pronto para gerenciar a persistência da caixa-preta de auditoria
}