package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProdutoRequestDTO(
        String nome,
        String descricao,
        BigDecimal preco,
        String urlImagem,
        StatusProduto status,
        Boolean isCombo,
        UUID categoriaId,           // Recebe apenas o ID da Categoria
        List<UUID> adicionaisIds    // Recebe apenas os IDs dos Adicionais
) {}