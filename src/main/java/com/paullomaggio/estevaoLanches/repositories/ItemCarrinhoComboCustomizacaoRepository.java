package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.ItemCarrinhoComboCustomizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItemCarrinhoComboCustomizacaoRepository extends JpaRepository<ItemCarrinhoComboCustomizacao, UUID> {
}