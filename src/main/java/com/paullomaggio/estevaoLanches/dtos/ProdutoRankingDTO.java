package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO contendo informações de um produto no ranking de vendas")
public class ProdutoRankingDTO {
    @Schema(description = "Nome do produto", example = "Hambúrguer Clássico")
    public String nomeProduto;
    @Schema(description = "Quantidade total vendida do produto no período", example = "150")
    private Long quantidadeVendida;
}