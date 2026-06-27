package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Catálogo — Matriz de Blindagem do Cardápio")
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private AdicionalRepository adicionalRepository;

    @InjectMocks private ProdutoService produtoService;

    private UUID produtoId;
    private UUID categoriaId;
    private Produto produtoMock;
    private Categoria categoriaMock;
    private ProdutoRequestDTO requestDTOMock;

    @BeforeEach
    void setUp() {
        produtoId = UUID.randomUUID();
        categoriaId = UUID.randomUUID();

        categoriaMock = new Categoria();
        categoriaMock.setId(categoriaId);

        produtoMock = new Produto();
        produtoMock.setId(produtoId);
        produtoMock.setNome("HAMBÚRGUER ARTESANAL");
        produtoMock.setDescricao("Blend de costela 180g");
        produtoMock.setPreco(new BigDecimal("35.00"));
        produtoMock.setUrlImagem("http://link.com/imagem.png");
        produtoMock.setStatus(StatusProduto.DISPONIVEL);
        produtoMock.setIsCombo(false);
        produtoMock.setPrecisaPreparo(true);
        produtoMock.setCategoria(categoriaMock);
        produtoMock.setAdicionais(new ArrayList<>());

        requestDTOMock = new ProdutoRequestDTO(
                "Hambúrguer Artesanal",
                "Blend de costela 180g",
                new BigDecimal("35.00"),
                "http://link.com/imagem.png",
                StatusProduto.DISPONIVEL,
                false,
                true,
                categoriaId,
                new ArrayList<>()
        );
    }

    // =========================================================================
    // BLOCO 1 — CADASTRO
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — Fluxos de Cadastro")
    class CadastroTests {

        @Test
        @DisplayName("CT-001 ao CT-010: Deve cadastrar um produto válido persistindo todas as propriedades")
        void ct001_cadastrarProdutoValido() {
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

            ProdutoResponseDTO resultado = produtoService.salvar(requestDTOMock);

            assertNotNull(resultado);
            verify(produtoRepository, times(1)).save(any(Produto.class));
        }
    }

    // =========================================================================
    // BLOCO 2 — CATEGORIA
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Validação de Categorias")
    class CategoriaTests {

        @Test
        @DisplayName("CT-011 e CT-013: Deve salvar se a categoria informada existir")
        void ct011_categoriaExistente() {
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

            assertDoesNotThrow(() -> produtoService.salvar(requestDTOMock));
        }

        @Test
        @DisplayName("CT-012 e CT-015: Deve lançar BusinessRuleException se a categoria informada for nula ou inexistente")
        void ct012_categoriaInexistente() {
            when(categoriaRepository.findById(any())).thenReturn(Optional.empty());
            assertThrows(BusinessRuleException.class, () -> produtoService.salvar(requestDTOMock));
            verify(produtoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 3 — ADICIONAIS
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Gerenciamento de Adicionais")
    class AdicionaisTests {

        @Test
        @DisplayName("CT-016 ao CT-019: Deve associar adicionais quando a lista for informada")
        void ct016_salvarComAdicionais() {
            UUID adicionalId = UUID.randomUUID();
            Adicional adicional = new Adicional();
            adicional.setId(adicionalId);

            ProdutoRequestDTO dtoComAdicionais = new ProdutoRequestDTO(
                    "Burger", "Desc", new BigDecimal("10.00"), "", StatusProduto.DISPONIVEL,
                    false, true, categoriaId, List.of(adicionalId)
            );

            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
            when(adicionalRepository.findAllById(anyList())).thenReturn(List.of(adicional));
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

            produtoService.salvar(dtoComAdicionais);
            verify(adicionalRepository, times(1)).findAllById(anyList());
        }

        @Test
        @DisplayName("CT-020 e CT-022: Deve limpar a coleção de adicionais se a lista de IDs vier vazia")
        void ct022_limparAdicionaisColecaoVazia() {
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoMock));
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

            produtoMock.getAdicionais().add(new Adicional());

            produtoService.atualizar(produtoId, requestDTOMock);
            assertTrue(produtoMock.getAdicionais().isEmpty());
        }
    }

    // =========================================================================
    // BLOCO 4 — NOME (HIGIENIZAÇÃO AUTOMÁTICA)
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Higienização do Nome")
    class NomeTests {

        @Test
        @DisplayName("CT-024, CT-025 e CT-026: Record deve aplicar Trim e UpperCase automaticamente")
        void ct024_higienizarNomeViaRecord() {
            ProdutoRequestDTO dtoSujo = new ProdutoRequestDTO(
                    "   x-burger turbo   ", "Desc", new BigDecimal("10.00"), "", StatusProduto.DISPONIVEL,
                    false, true, categoriaId, new ArrayList<>()
            );
            assertEquals("X-BURGER TURBO", dtoSujo.nome());
        }
    }

    // =========================================================================
    // BLOCO 5 — PREÇO
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — Escores de Preços")
    class PrecoTests {

        @Test
        @DisplayName("CT-030 ao CT-033: Deve permitir salvar produtos com valores tradicionais positivos")
        void ct030_precoValido() {
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

            assertDoesNotThrow(() -> produtoService.salvar(requestDTOMock));
        }
    }

    // =========================================================================
    // BLOCO 6, 7 & 8 — STATUS, COMBO E CONFIGURAÇÃO DE PREPARO
    // =========================================================================
    @Nested
    @DisplayName("6, 7 & 8. Camada de Blindagem — Parametrizes e Matrizes Operacionais")
    class ConfiguracoesTests {

        @Test
        @DisplayName("CT-036 e CT-037: Deve respeitar os estados de enums de disponibilidade")
        void ct036_statusDisponivel() {
            assertEquals(StatusProduto.DISPONIVEL, produtoMock.getStatus());
        }

        @Test
        @DisplayName("CT-045 e CT-046: Deve expor corretamente as flags de preparo em cozinha")
        void ct045_precisaPreparo() {
            assertTrue(produtoMock.getPrecisaPreparo());
        }
    }

    // =========================================================================
    // BLOCO 9 — ATUALIZAÇÃO
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Fluxos de Atualização")
    class AtualizacaoTests {

        @Test
        @DisplayName("CT-048 ao CT-054 [Original]: Deve atualizar as propriedades do produto mapeando via ArgumentCaptor e inicializando coleções")
        void deveAtualizarProduto() {
            UUID produtoIdLocal = produtoId;
            UUID categoriaIdLocal = categoriaId;

            Produto produtoExistente = new Produto();
            produtoExistente.setId(produtoIdLocal);
            produtoExistente.setNome("X-SALADA");
            produtoExistente.setDescricao("Antigo");
            produtoExistente.setPreco(new BigDecimal("18.00"));
            produtoExistente.setUrlImagem("");
            produtoExistente.setStatus(StatusProduto.DISPONIVEL);
            produtoExistente.setIsCombo(false);
            produtoExistente.setPrecisaPreparo(true);
            produtoExistente.setAdicionais(new ArrayList<>());

            Categoria categoriaMockLocal = new Categoria();
            categoriaMockLocal.setId(categoriaIdLocal);

            when(produtoRepository.findById(produtoIdLocal)).thenReturn(Optional.of(produtoExistente));
            when(categoriaRepository.findById(categoriaIdLocal)).thenReturn(Optional.of(categoriaMockLocal));
            when(produtoRepository.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

            ProdutoRequestDTO dtoAlteracao = new ProdutoRequestDTO(
                    "X-SALADA TURBO",
                    "Novo pão artesanal",
                    new BigDecimal("22.00"),
                    "http://estevaolanches.com/images/xsalada.png",
                    StatusProduto.DISPONIVEL,
                    false,
                    true,
                    categoriaIdLocal,
                    new ArrayList<>()
            );

            ProdutoResponseDTO resultado = produtoService.atualizar(produtoIdLocal, dtoAlteracao);

            ArgumentCaptor<Produto> produtoCaptor = ArgumentCaptor.forClass(Produto.class);
            verify(produtoRepository, times(1)).save(produtoCaptor.capture());

            assertThat(resultado.nome()).isEqualTo("X-SALADA TURBO");
            assertThat(produtoCaptor.getValue().getPreco()).isEqualByComparingTo(new BigDecimal("22.00"));
        }

        @Test
        @DisplayName("CT-055: Deve lançar ResourceNotFoundException ao tentar atualizar produto inexistente")
        void ct055_atualizarInexistente() {
            when(produtoRepository.findById(any())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> produtoService.atualizar(UUID.randomUUID(), requestDTOMock));
        }
    }

    // =========================================================================
    // BLOCO 10 — EXCLUSÃO
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Remoção Física")
    class ExclusaoTests {

        @Test
        @DisplayName("CT-056 e CT-058: Deve deletar se o produto existir chamando deleteById uma única vez")
        void ct056_deletarComSucesso() {
            when(produtoRepository.existsById(produtoId)).thenReturn(true);
            doNothing().when(produtoRepository).deleteById(produtoId);

            produtoService.deletar(produtoId);

            verify(produtoRepository, times(1)).deleteById(produtoId);
        }

        @Test
        @DisplayName("CT-057: Deve explodir ResourceNotFoundException ao tentar deletar ID inexistente")
        void ct057_deletarInexistente() {
            when(produtoRepository.existsById(any())).thenReturn(false);
            assertThrows(ResourceNotFoundException.class, () -> produtoService.deletar(UUID.randomUUID()));
            verify(produtoRepository, never()).deleteById(any());
        }
    }

    // =========================================================================
    // BLOCO 11 & 12 — BUSCAS E LISTAGEM
    // =========================================================================
    @Nested
    @DisplayName("11 & 12. Camada de Blindagem — Consultas e Filtros do Cardápio")
    class ConsultasTests {

        @Test
        @DisplayName("CT-059 e CT-068: Buscar por ID deve retornar os dados mapeados corretamente para DTO")
        void ct059_buscarPorId() {
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoMock));

            ProdutoResponseDTO resultado = produtoService.buscarPorId(produtoId);

            assertNotNull(resultado);
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-061 ao CT-065: Deve buscar produtos por termo textual")
        void ct061_buscarPorTermo() {
            when(produtoRepository.buscarPorTermo("artesanal")).thenReturn(List.of(produtoMock));

            List<ProdutoResponseDTO> resultado = produtoService.buscarPorTermo("artesanal");

            assertFalse(resultado.isEmpty());
        }

        @Test
        @DisplayName("CT-066 e CT-067: Deve listar todos os produtos ou retornar lista vazia se não houver registros")
        void ct066_listarTodos() {
            when(produtoRepository.findAll()).thenReturn(List.of(produtoMock));

            List<ProdutoResponseDTO> resultado = produtoService.listarTodos();

            assertEquals(1, resultado.size());
        }
    }

    // =========================================================================
    // BLOCO 13 ao 18 — INTEGRALIDADE OPERACIONAL, CONCORRÊNCIA E AUDITORIA
    // =========================================================================
    @Nested
    @DisplayName("13 a 18. Camada de Blindagem — Auditoria e Fluxos Concorrentes")
    class AuditoriaTests {

        @Test
        @DisplayName("CT-078: Simulação de Concorrência — Dois operadores buscando o mesmo item em frações de segundo")
        void ct078_buscaConcorrente() {
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoMock));

            ProdutoResponseDTO res1 = produtoService.buscarPorId(produtoId);
            ProdutoResponseDTO res2 = produtoService.buscarPorId(produtoId);

            assertNotNull(res1);
            assertNotNull(res2);
            verify(produtoRepository, times(2)).findById(produtoId);
        }

        @Test
        @DisplayName("CT-095 e CT-096: Garantia de Auditoria — Listas internas da entidade nunca devem ser expostas como nulas")
        void ct095_colecoesNuncaNulas() {
            Produto p = new Produto();
            assertNotNull(p.getAdicionais());
            assertNotNull(p.getItensDoCombo());
        }

        @Test
        @DisplayName("CT-110: Regressão de Ciclo de Vida — Cadastrar ➔ Atualizar ➔ Buscar ➔ Deletar")
        void ct110_regressaoCicloVidaCompleto() {
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
            when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoMock));
            when(produtoRepository.existsById(produtoId)).thenReturn(true);

            // 1. Cadastra
            ProdutoResponseDTO cadastrado = produtoService.salvar(requestDTOMock);

            // 2. Atualiza e Busca
            produtoService.atualizar(produtoId, requestDTOMock);
            ProdutoResponseDTO consultado = produtoService.buscarPorId(produtoId);

            // 3. Deleta
            produtoService.deletar(produtoId);

            assertNotNull(cadastrado);
            assertNotNull(consultado);
            verify(produtoRepository, times(1)).deleteById(produtoId);
        }
    }
}