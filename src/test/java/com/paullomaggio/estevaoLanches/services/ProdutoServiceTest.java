package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Testes de Serviço — ProdutoService")
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProdutoService produtoService;

    @Test
    @DisplayName("Deve atualizar as propriedades do produto mapeando via ArgumentCaptor e inicializando coleções")
    void deveAtualizarProduto() {
        UUID produtoId = UUID.randomUUID();
        UUID categoriaId = UUID.randomUUID();

        Produto produtoExistente = new Produto();
        produtoExistente.setId(produtoId);
        produtoExistente.setNome("X-SALADA");
        produtoExistente.setDescricao("Antigo");
        produtoExistente.setPreco(new BigDecimal("18.00"));
        produtoExistente.setUrlImagem("");
        produtoExistente.setStatus(StatusProduto.DISPONIVEL);
        produtoExistente.setIsCombo(false);
        produtoExistente.setPrecisaPreparo(true);
        produtoExistente.setAdicionais(new ArrayList<>()); // Blindagem para o clear() de adicionais

        // 🎯 FIX DEFINITIVO: Como coleções JPA são inicializadas inline na entidade,
        // a linha "setItensCombo" foi removida para evitar o erro de compilação,
        // pois o Hibernate já garante que a lista nasce vazia e não-nula!

        Categoria categoriaMock = new Categoria();
        categoriaMock.setId(categoriaId);

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoExistente));
        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        ProdutoRequestDTO dtoAlteracao = new ProdutoRequestDTO(
                "X-SALADA TURBO",
                "Novo pão artesanal",
                new BigDecimal("22.00"),
                "http://estevaolanches.com/images/xsalada.png",
                StatusProduto.DISPONIVEL,
                false,
                true,
                categoriaId,
                new ArrayList<>()
        );

        // Execução do método de serviço sob teste
        ProdutoResponseDTO resultado = produtoService.atualizar(produtoId, dtoAlteracao);

        // Verificação e Auditoria do estado final do objeto modificado
        ArgumentCaptor<Produto> produtoCaptor = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository, times(1)).save(produtoCaptor.capture());

        assertThat(resultado.nome()).isEqualTo("X-SALADA TURBO");
        assertThat(produtoCaptor.getValue().getPreco()).isEqualByComparingTo(new BigDecimal("22.00"));
    }
}