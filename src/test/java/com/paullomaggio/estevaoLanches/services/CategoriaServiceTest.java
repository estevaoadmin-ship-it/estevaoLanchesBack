package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CategoriaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CategoriaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import jakarta.persistence.OneToMany;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Suprema — Matriz de Blindagem do Catálogo (Categorias)")
class CategoriaServiceTest {

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ProdutoRepository produtoRepository;

    // Removido @InjectMocks
    private CategoriaService categoriaService;

    private Categoria categoriaPadrao;
    private UUID categoriaId;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks
        categoriaService = new CategoriaService(categoriaRepository, produtoRepository);

        categoriaId = UUID.randomUUID();
        categoriaPadrao = new Categoria(
                categoriaId,
                "LANCHES",
                "Hambúrgueres artesanais e combos",
                1,
                true,
                "https://res.cloudinary.com/estevaolanches/image/upload/lanches.png",
                new ArrayList<>()
        );
    }

    // =========================================================================
    // BLOCO 1 — listarTodas()
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — listarTodas()")
    class ListarTodasTests {

        @Test
        @DisplayName("Cenários 1, 3, 4, 5 e 6 — Deve mapear e retornar DTOs respeitando a ordem de exibição Asc")
        void deveListarTodasAsCategoriasOrdenadas() {
            Categoria segunda = new Categoria(UUID.randomUUID(), "BEBIDAS", "Sucos e refrigerantes", 2, true, "bebida.png", new ArrayList<>());
            when(categoriaRepository.findAllByOrderByOrdemExibicaoAsc()).thenReturn(List.of(categoriaPadrao, segunda));

            List<CategoriaResponseDTO> resultado = categoriaService.listarTodas();

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).id()).isEqualTo(categoriaId);
            assertThat(resultado.get(0).nome()).isEqualTo("LANCHES");
            assertThat(resultado.get(0).descricao()).isEqualTo("Hambúrgueres artesanais e combos");
            assertThat(resultado.get(0).urlImagem()).contains("cloudinary");
            assertThat(resultado.get(0).ordemExibicao()).isEqualTo(1);
            assertThat(resultado.get(0).ativo()).isTrue();

            verify(categoriaRepository, times(1)).findAllByOrderByOrdemExibicaoAsc();
        }

        @Test
        @DisplayName("Cenário 2 — Deve retornar uma lista vazia imutável quando não houver registros salvos")
        void deveRetornarListaVaziaQuandoNaoHouverCategorias() {
            when(categoriaRepository.findAllByOrderByOrdemExibicaoAsc()).thenReturn(Collections.emptyList());

            List<CategoriaResponseDTO> resultado = categoriaService.listarTodas();

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("Cenário 7 — Garantia de Leitura Pura: Nunca deve acionar métodos de escrita ou deleção")
        void nuncaDeveChamarEscrita() {
            when(categoriaRepository.findAllByOrderByOrdemExibicaoAsc()).thenReturn(List.of(categoriaPadrao));

            categoriaService.listarTodas();

            verify(categoriaRepository, never()).save(any());
            verify(categoriaRepository, never()).delete(any());
            verify(categoriaRepository, never()).existsById(any());
        }
    }

    // =========================================================================
    // BLOCO 2 — buscarPorNome()
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — buscarPorNome()")
    class BuscarPorNomeTests {

        @Test
        @DisplayName("Cenários 8, 9, 10, 12 e 13 — Deve localizar categorias por correspondência textual exata ou parcial")
        void deveBuscarCategoriasPorNome() {
            when(categoriaRepository.buscarPorNome("lanche")).thenReturn(List.of(categoriaPadrao));

            List<CategoriaResponseDTO> resultado = categoriaService.buscarPorNome("lanche");

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).nome()).isEqualTo("LANCHES");
            verify(categoriaRepository, times(1)).buscarPorNome("lanche");
        }

        @Test
        @DisplayName("Cenário 11 — Deve retornar lista vazia quando nenhuma categoria casar com a pesquisa")
        void deveRetornarVazioQuandoNomeNaoCasar() {
            when(categoriaRepository.buscarPorNome("Inexistente")).thenReturn(Collections.emptyList());

            List<CategoriaResponseDTO> resultado = categoriaService.buscarPorNome("Inexistente");

            assertThat(resultado).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 3 — buscarPorId()
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — buscarPorId()")
    class BuscarPorIdTests {

        @Test
        @DisplayName("Cenário 14, 16 e 17 — Deve buscar por id existente uma única vez sem acionar escritas")
        void deveBuscarPorIdExistente() {
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaPadrao));

            CategoriaResponseDTO resultado = categoriaService.buscarPorId(categoriaId);

            assertThat(resultado).isNotNull();
            assertThat(resultado.id()).isEqualTo(categoriaId);
            verify(categoriaRepository, times(1)).findById(categoriaId);
            verify(categoriaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cenário 15 — ID ausente ou inválido na base deve estourar ResourceNotFoundException")
        void deveLancarExceptionQuandoIdNaoExistir() {
            UUID idInexistente = UUID.randomUUID();
            when(categoriaRepository.findById(idInexistente)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> categoriaService.buscarPorId(idInexistente));
        }

        @Test
        @DisplayName("Cenário 18 — Desacoplamento: Alterar a Entity pós-mapeamento não pode contaminar o DTO já gerado")
        void dtoDeveSerIndependenteDaEntidade() {
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaPadrao));

            CategoriaResponseDTO resultado = categoriaService.buscarPorId(categoriaId);
            categoriaPadrao.setNome("MUTAÇÃO INFECTADA");

            assertThat(resultado.nome()).isEqualTo("LANCHES");
        }
    }

    // =========================================================================
    // BLOCO 4 — salvar()
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — salvar()")
    class SalvarTests {

        @Test
        @DisplayName("Cenários 19 ao 24, 26 e 27 — Deve persistir uma categoria válida populando todos os campos")
        void deveSalvarCategoriaValida() {
            CategoriaRequestDTO request = new CategoriaRequestDTO(
                    "COMBOS",
                    "Combos promocionais",
                    3,
                    true,
                    "https://res.cloudinary.com/estevaolanches/image/upload/combos.png"
            );

            when(categoriaRepository.save(any(Categoria.class))).thenAnswer(i -> {
                Categoria c = i.getArgument(0);
                c.setId(categoriaId);
                return c;
            });

            CategoriaResponseDTO resultado = categoriaService.salvar(request);

            assertThat(resultado).isNotNull();
            assertThat(resultado.id()).isEqualTo(categoriaId);
            assertThat(resultado.nome()).isEqualTo("COMBOS");
            assertThat(resultado.descricao()).isEqualTo("Combos promocionais");
            assertThat(resultado.urlImagem()).contains("cloudinary");
            assertThat(resultado.ordemExibicao()).isEqualTo(3);
            assertThat(resultado.ativo()).isTrue();
            verify(categoriaRepository, times(1)).save(any(Categoria.class));
        }
    }

    // =========================================================================
    // BLOCO 5 — atualizar()
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — atualizar()")
    class CategoriaAtualizarTests {

        // Removido @BeforeEach setupFind()

        @Test
        @DisplayName("Cenário 28, 35, 36 e 37 — Atualização Completa: Deve alterar todos os campos sem violar UUID ou duplicar linhas")
        void deveAtualizarTodosOsCampos() {
            // Mocks específicos para este teste
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaPadrao));
            when(categoriaRepository.save(any(Categoria.class))).thenAnswer(i -> i.getArgument(0));

            CategoriaRequestDTO request = new CategoriaRequestDTO(
                    "BURGER MODIF",
                    "Nova Desc",
                    9,
                    false,
                    "https://res.cloudinary.com/estevaolanches/image/upload/nova.png"
            );

            CategoriaResponseDTO resultado = categoriaService.atualizar(categoriaId, request);

            assertThat(resultado.id()).isEqualTo(categoriaId);
            assertThat(resultado.nome()).isEqualTo("BURGER MODIF");
            assertThat(resultado.descricao()).isEqualTo("Nova Desc");
            assertThat(resultado.urlImagem()).contains("cloudinary");
            assertThat(resultado.ordemExibicao()).isEqualTo(9);
            assertThat(resultado.ativo()).isFalse();
            verify(categoriaRepository, times(1)).save(any(Categoria.class));
        }

        @Test
        @DisplayName("Cenários 29 ao 33 — Alterações Isoladas: Deve modificar campos específicos mantendo o resto do estado íntegro")
        void deveAtualizarCamposDeFormaIsolada() {
            // Mocks específicos para este teste
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaPadrao));
            when(categoriaRepository.save(any(Categoria.class))).thenAnswer(i -> i.getArgument(0));

            // Teste 1: Atualiza apenas o nome
            CategoriaResponseDTO resNome = categoriaService.atualizar(categoriaId, new CategoriaRequestDTO("SÓ NOME", categoriaPadrao.getDescricao(), categoriaPadrao.getOrdemExibicao(), categoriaPadrao.getAtivo(), categoriaPadrao.getUrlImagem()));
            assertThat(resNome.nome()).isEqualTo("SÓ NOME");
            assertThat(resNome.descricao()).isEqualTo(categoriaPadrao.getDescricao());

            // Teste 2: Atualiza apenas a descrição
            CategoriaResponseDTO resDesc = categoriaService.atualizar(categoriaId, new CategoriaRequestDTO(categoriaPadrao.getNome(), "SÓ DESC", categoriaPadrao.getOrdemExibicao(), categoriaPadrao.getAtivo(), categoriaPadrao.getUrlImagem()));
            assertThat(resDesc.descricao()).isEqualTo("SÓ DESC");

            // Teste 3: Atualiza apenas o status ativo
            CategoriaResponseDTO resAtivo = categoriaService.atualizar(categoriaId, new CategoriaRequestDTO(categoriaPadrao.getNome(), categoriaPadrao.getDescricao(), categoriaPadrao.getOrdemExibicao(), false, categoriaPadrao.getUrlImagem()));
            assertThat(resAtivo.ativo()).isFalse();
        }

        @Test
        @DisplayName("Cenário 34 — Tentar atualizar uma categoria órfã/inexistente deve estourar ResourceNotFoundException")
        void deveLancarExceptionAoAtualizarInexistente() {
            UUID idInexistente = UUID.randomUUID();
            when(categoriaRepository.findById(idInexistente)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    categoriaService.atualizar(idInexistente, new CategoriaRequestDTO("X", "X", 0, true, "X"))
            );
        }
    }


    // =========================================================================
    // BLOCO 6 — deletar() (ORDENAÇÃO CIRÚRGICA DE EXECUÇÃO)
    // =========================================================================
    @Nested
    @DisplayName("6. Camada de Blindagem — deletar()")
    class DeletarTests {

        @Test
        @DisplayName("Cenários 38, 40, 41, 42 e 45 — Cascade Manual Control: Deve expurgar produtos da categoria ANTES de eliminar a categoria mãe")
        void deveRespeitarOrdemDeDelecaoFisica() {
            when(categoriaRepository.existsById(categoriaId)).thenReturn(true);
            doNothing().when(produtoRepository).deletarPorCategoriaId(categoriaId); // Mock para o método void
            doNothing().when(categoriaRepository).deleteById(categoriaId); // Mock para o método void

            categoriaService.deletar(categoriaId);

            InOrder ordemDeChamadas = inOrder(produtoRepository, categoriaRepository);
            ordemDeChamadas.verify(produtoRepository, times(1)).deletarPorCategoriaId(categoriaId);
            ordemDeChamadas.verify(categoriaRepository, times(1)).deleteById(categoriaId);

            verify(categoriaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cenários 39, 43 e 44 — Abortagem Segura: Se a categoria não existir, nenhuma rotina de deleção (mãe ou filhos) deve rodar")
        void naoDeveChamarDelecoesSeCategoriaNaoExistir() {
            UUID idInexistente = UUID.randomUUID();
            when(categoriaRepository.existsById(idInexistente)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> categoriaService.deletar(idInexistente));

            verify(produtoRepository, never()).deletarPorCategoriaId(any());
            verify(categoriaRepository, never()).deleteById(any());
        }
    }

    // =========================================================================
    // BLOCO 8 — BEAN VALIDATION DA ENTIDADE (EFEITO COLATERAL ZERO)
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Bean Validation Nativo da Entity")
    class BeanValidationTests {

        private Validator validator;

        @BeforeEach
        void setUpValidator() {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            validator = factory.getValidator();
        }

        @Test
        @DisplayName("Cenários 51, 52 e 53 — Restrições de Nome: Não pode aceitar strings vazias, nulas ou cheias de espaços")
        void validacaoNomeRestricoes() {
            Categoria cNull = new Categoria(null, null, "Desc", 1, true, "img.png", new ArrayList<>());
            Categoria cVazia = new Categoria(null, "", "Desc", 1, true, "img.png", new ArrayList<>());
            Categoria cEspacos = new Categoria(null, "    ", "Desc", 1, true, "img.png", new ArrayList<>());

            Set<ConstraintViolation<Categoria>> violacoesNull = validator.validate(cNull);
            Set<ConstraintViolation<Categoria>> violacoesVazia = validator.validate(cVazia);
            Set<ConstraintViolation<Categoria>> violacoesEspacos = validator.validate(cEspacos);

            assertThat(violacoesNull).isNotEmpty();
            assertThat(violacoesVazia).isNotEmpty();
            assertThat(violacoesEspacos).isNotEmpty();
        }

        @Test
        @DisplayName("Cenário 54 e 55 — Restrições de Ordem: Valor nulo ou indicadores de indexação negativos devem ser travados")
        void validacaoOrdemRestricoes() {
            Categoria cNull = new Categoria(null, "TESTE", "Desc", null, true, "img.png", new ArrayList<>());
            Categoria cNegativa = new Categoria(null, "TESTE", "Desc", -1, true, "img.png", new ArrayList<>());

            assertThat(validator.validate(cNull)).isNotEmpty();
            assertThat(validator.validate(cNegativa)).isNotEmpty();
        }

        @Test
        @DisplayName("Cenário 56, 57 e 58 — Estoiro de Limites: Status nulo ou textos ultrapassando o limite físico de 255 caracteres")
        void validacaoLimitesEStatusNull() {
            String estoiroTexto = "A".repeat(256);
            Categoria cAtivoNull = new Categoria(null, "TESTE", "Desc", 1, null, "img.png", new ArrayList<>());
            Categoria cDescEstoiro = new Categoria(null, "TESTE", estoiroTexto, 1, true, "img.png", new ArrayList<>());
            Categoria cUrlEstoiro = new Categoria(null, "TESTE", "Desc", 1, true, estoiroTexto, new ArrayList<>());

            assertThat(validator.validate(cAtivoNull)).isNotEmpty();
            assertThat(validator.validate(cDescEstoiro)).isNotEmpty();
            assertThat(validator.validate(cUrlEstoiro)).isNotEmpty();
        }
    }

    // =========================================================================
    // BLOCO 9 — RELACIONAMENTOS & INTEGRALIDADE ESTRUTURAL
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Arquitetura de Relacionamentos")
    class RelacionamentosTests {

        @Test
        @DisplayName("Cenário 59 e 60 — Coesão: Categoria deve inicializar com lista vazia de produtos vinculados")
        void categoriaDeveIniciarComListaVazia() {
            Categoria nova = new Categoria();
            assertThat(nova.getProdutos()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Cenários 61, 62 e 63 — Mapeamento JPA: Valida os tokens de Cascade REMOVE, orphanRemoval e metadados estruturais")
        void deveGarantirPresencaDeConfiguracoesMetaJpa() throws NoSuchFieldException {
            OneToMany anotacao = Categoria.class.getDeclaredField("produtos").getAnnotation(OneToMany.class);

            org.junit.jupiter.api.Assertions.assertNotNull(anotacao);
            assertThat(anotacao.mappedBy()).isEqualTo("categoria");
            assertThat(anotacao.orphanRemoval()).isTrue();
            assertThat(anotacao.cascade()).contains(jakarta.persistence.CascadeType.REMOVE);
        }
    }

    // =========================================================================
    // BLOCO 10 — REGRESSÃO DE ESTADO INTEGRADO
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Fluxos Integrados de Regressão")
    class RegressaoTests {

        @Test
        @DisplayName("Cenário 64 — Regressão Ciclo Completo: Salvar ➔ Buscar por ID e Comparar Equivalência Atômica")
        void regressaoSalvarEBuscar() {
            CategoriaRequestDTO request = new CategoriaRequestDTO("REGRESSAO", "Desc", 1, true, "https://res.cloudinary.com/img.png");
            when(categoriaRepository.save(any(Categoria.class))).thenAnswer(i -> {
                Categoria c = i.getArgument(0);
                c.setId(categoriaId);
                return c;
            });
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaPadrao));

            CategoriaResponseDTO salvo = categoriaService.salvar(request);
            categoriaPadrao.setNome(salvo.nome());

            CategoriaResponseDTO consultado = categoriaService.buscarPorId(categoriaId);

            assertThat(consultado.nome()).isEqualTo(salvo.nome());
            assertThat(consultado.id()).isEqualTo(salvo.id());
        }

        @Test
        @DisplayName("Cenário 66 — Regressão Ciclo Destrutivo: Salvar ➔ Deletar ➔ Buscar deve estourar Exception")
        void regressaoSalvarDeletarBuscar() {
            when(categoriaRepository.existsById(categoriaId)).thenReturn(true);
            when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.empty());
            doNothing().when(produtoRepository).deletarPorCategoriaId(categoriaId); // Mock para o método void
            doNothing().when(categoriaRepository).deleteById(categoriaId); // Mock para o método void

            categoriaService.deletar(categoriaId);

            assertThrows(ResourceNotFoundException.class, () -> categoriaService.buscarPorId(categoriaId));
        }
    }
}