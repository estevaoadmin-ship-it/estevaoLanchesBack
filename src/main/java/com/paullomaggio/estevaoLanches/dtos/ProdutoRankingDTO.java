package com.paullomaggio.estevaoLanches.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoRankingDTO {
    public String nomeProduto;
    private Long quantidadeVendida;
}