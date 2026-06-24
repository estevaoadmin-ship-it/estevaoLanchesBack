package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    @Query("SELECT c FROM Categoria c " +
            "WHERE LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "ORDER BY c.ordemExibicao ASC")
    List<Categoria> buscarPorNome(@Param("nome") String nome);

    List<Categoria> findAllByOrderByOrdemExibicaoAsc();
}