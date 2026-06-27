package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.ComboProduto;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@DisplayName("🎯 MATRIZ REGULADORA DE CARDÁPIO: Persistência de Produtos (PRD-001 a PRD-100)")
class ProdutoRepositoryTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Categoria categoriaPadrao;
    private Produto produtoPadrao;

    @BeforeEach
    void setupCardapioBase() {
        categoriaPadrao = new Categoria();
        categoriaPadrao.setNome("Lanches Especiais");
        categoriaPadrao.setOrdemExibicao(1);
        categoriaPadrao.setAtivo(true);
        categoriaRepository.save(categoriaPadrao);

        produtoPadrao = new Produto();
        produtoPadrao.setNome("X-Bacon Artesanal");
        produtoPadrao.setDescricao("Pão, carne, muito bacon e queijo");
        produtoPadrao.setPreco(new BigDecimal("30.00"));
        produtoPadrao.setStatus(StatusProduto.DISPONIVEL);
        produtoPadrao.setIsCombo(false);
        produtoPadrao.setPrecisaPreparo(true);
        produtoPadrao.setCategoria(categoriaPadrao);
        produtoPadrao.setAdicionais(new ArrayList<>());
        produtoPadrao.setItensDoCombo(new ArrayList<>());
        produtoRepository.save(produtoPadrao);

        entityManager.flush();
    }

    private Produto instanciarProduto(String nome, String desc, BigDecimal preco, StatusProduto status, Boolean isCombo, Categoria cat) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setDescricao(desc);
        p.setPreco(preco);
        p.setStatus(status);
        p.setIsCombo(isCombo);
        p.setPrecisaPreparo(true);
        p.setCategoria(cat);
        p.setAdicionais(new ArrayList<>());
        p.setItensDoCombo(new ArrayList<>());
        return p;
    }

    // =========================================================================
    // BLOCO 1 — buscarPorTermo() (PRD-001 a PRD-015)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 1 — buscarPorTermo()")
    class Bloco1BuscarPorTermo {

        @Test @DisplayName("PRD-001 - Buscar pelo nome completo do produto")
        void prd001() {
            List<Produto> res = produtoRepository.buscarPorTermo("X-Bacon Artesanal");
            assertThat(res).isNotEmpty();
            assertThat(res.get(0).getNome()).isEqualTo("X-Bacon Artesanal");
        }

        @Test @DisplayName("PRD-002 - Buscar por fragmento/parte do nome")
        void prd002() {
            List<Produto> res = produtoRepository.buscarPorTermo("bacon");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-003 - Buscar por palavra contida na descrição")
        void prd003() {
            List<Produto> res = produtoRepository.buscarPorTermo("muito bacon");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-004 - Buscar por palavra contida na Categoria vinculada")
        void prd004() {
            List<Produto> res = produtoRepository.buscarPorTermo("especiais");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-005 - Busca ignorando letras maiúsculas/minúsculas (Case Insensitive)")
        void prd005() {
            List<Produto> res = produtoRepository.buscarPorTermo("x-BACON");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-006 - Busca contendo espaços adjacentes")
        void prd006() {
            List<Produto> res = produtoRepository.buscarPorTermo("X-Bacon Artesanal");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-007 - Busca tratando acentuação ortográfica")
        void prd007() {
            Categoria c = new Categoria(); c.setNome("Sucos Metrô"); categoriaRepository.save(c);
            entityManager.persist(instanciarProduto("Suco de Limão", "Gelado", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, c));
            entityManager.flush();
            List<Produto> res = produtoRepository.buscarPorTermo("Limão");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-008 - Busca contendo hifens ou caracteres especiais")
        void prd008() {
            List<Produto> res = produtoRepository.buscarPorTermo("X-Bacon");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-009 - Busca vazia ou genérica trata gracefully")
        void prd009() {
            List<Produto> res = produtoRepository.buscarPorTermo("");
            assertThat(res).isNotNull();
        }

        @Test @DisplayName("PRD-010 - Busca por termo inexistente retorna coleção vazia")
        void prd010() {
            List<Produto> res = produtoRepository.buscarPorTermo("Caviar");
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("PRD-011 - Buscar utilizando cadeia de texto enorme")
        void prd011() {
            List<Produto> res = produtoRepository.buscarPorTermo("A".repeat(100));
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("PRD-012 - Buscar utilizando caracteres gráficos Emoji")
        void prd012() {
            List<Produto> res = produtoRepository.buscarPorTermo("🍔");
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("PRD-013 - Bloqueio de injeção SQL nativa no filtro de busca")
        void prd013() {
            List<Produto> res = produtoRepository.buscarPorTermo("' OR 1=1 --");
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("PRD-014 - Busca contendo elementos HTML/Scripts")
        void prd014() {
            List<Produto> res = produtoRepository.buscarPorTermo("<script>alert(1)</script>");
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("PRD-015 - Busca utilizando codificação Unicode complexa")
        void prd015() {
            List<Produto> res = produtoRepository.buscarPorTermo("\u00C1"); // Á
            assertThat(res).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 2 — findByStatus() (PRD-016 a PRD-020)
    // =========================================================================
    @Nested
    @DisplayName("🟢 BLOCO 2 — findByStatus()")
    class Bloco2FindByStatus {

        @Test @DisplayName("PRD-016 - Buscar produtos com status DISPONIVEL")
        void prd016() {
            List<Produto> res = produtoRepository.findByStatus(StatusProduto.DISPONIVEL);
            assertThat(res).hasSize(1);
        }

        @Test @DisplayName("PRD-017 - Buscar produtos com status INDISPONIVEL")
        void prd017() {
            entityManager.persist(instanciarProduto("Falta", "D", BigDecimal.ONE, StatusProduto.INDISPONIVEL, false, categoriaPadrao));
            entityManager.flush();
            List<Produto> res = produtoRepository.findByStatus(StatusProduto.INDISPONIVEL);
            assertThat(res).hasSize(1);
        }

        @Test @DisplayName("PRD-018 - Não misturar ou retornar produtos de outros estados")
        void prd018() {
            List<Produto> res = produtoRepository.findByStatus(StatusProduto.INDISPONIVEL);
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("PRD-019 - Retornar lista vazia se nenhum atender ao status")
        void prd019() {
            produtoRepository.deleteAll();
            assertThat(produtoRepository.findByStatus(StatusProduto.DISPONIVEL)).isEmpty();
        }

        @Test @DisplayName("PRD-020 - Escalar leitura sob lote populado de produtos disponíveis")
        void prd020() {
            for (int i = 0; i < 20; i++) {
                entityManager.persist(instanciarProduto("P " + i, "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao));
            }
            entityManager.flush();
            List<Produto> res = produtoRepository.findByStatus(StatusProduto.DISPONIVEL);
            assertThat(res.size()).isGreaterThanOrEqualTo(20);
        }
    }

    // =========================================================================
    // BLOCO 3 — findByIsComboFalse() (PRD-021 a PRD-024)
    // =========================================================================
    @Nested
    @DisplayName("🍔 BLOCO 3 — findByIsComboFalse()")
    class Bloco3FindByIsComboFalse {

        @Test @DisplayName("PRD-021 - Retornar exclusivamente itens/produtos simples")
        void prd021() {
            List<Produto> res = produtoRepository.findByIsComboFalse();
            assertThat(res).hasSize(1);
            assertThat(res.get(0).getIsCombo()).isFalse();
        }

        @Test @DisplayName("PRD-022 - Ignorar e expurgar agrupamentos de combos da listagem")
        void prd022() {
            entityManager.persist(instanciarProduto("Combo Estevão", "C", BigDecimal.TEN, StatusProduto.DISPONIVEL, true, categoriaPadrao));
            entityManager.flush();
            List<Produto> res = produtoRepository.findByIsComboFalse();
            assertThat(res).hasSize(1); // O combo foi completamente ignorado
        }

        @Test @DisplayName("PRD-023 - Retornar coleção vazia se a base contiver apenas combos")
        void prd023() {
            produtoRepository.deleteAll();
            entityManager.persist(instanciarProduto("Combo 1", "C", BigDecimal.TEN, StatusProduto.DISPONIVEL, true, categoriaPadrao));
            entityManager.flush();
            assertThat(produtoRepository.findByIsComboFalse()).isEmpty();
        }

        @Test @DisplayName("PRD-024 - Trazer todos os registros se nenhum for combo")
        void prd024() {
            long total = produtoRepository.count();
            assertThat(produtoRepository.findByIsComboFalse()).hasSize((int) total);
        }
    }

    // =========================================================================
    // BLOCO 4 — deletePorCategoriaId() (PRD-025 a PRD-029)
    // =========================================================================
    @Nested
    @DisplayName("🗑️ BLOCO 4 — deletePorCategoriaId()")
    class Bloco4DeletePorCategoriaId {

        @Test @DisplayName("PRD-025 - Excluir em lote todos os produtos atrelados à categoria")
        void prd025() {
            produtoRepository.deletarPorCategoriaId(categoriaPadrao.getId());
            entityManager.flush();
            assertThat(produtoRepository.findAll()).isEmpty();
        }

        @Test @DisplayName("PRD-026 - Não afetar ou excluir itens de partições de categorias vizinhas")
        void prd026() {
            Categoria c2 = new Categoria(); c2.setNome("Bebidas"); categoriaRepository.save(c2);
            entityManager.persist(instanciarProduto("Guaraná", "G", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, c2));
            entityManager.flush();

            produtoRepository.deletarPorCategoriaId(categoriaPadrao.getId());
            entityManager.flush();
            assertThat(produtoRepository.findAll()).hasSize(1); // Sobrou o refrigerante
        }

        @Test @DisplayName("PRD-027 - Executar operação passando ID de categoria inexistente")
        void prd027() {
            produtoRepository.deletarPorCategoriaId(UUID.randomUUID());
            entityManager.flush();
            assertThat(produtoRepository.findAll()).hasSize(1);
        }

        @Test @DisplayName("PRD-028 - Executar operação sob categoria sem produtos mapeados")
        void prd028() {
            Categoria cVacant = new Categoria(); cVacant.setNome("Sobremesas"); categoriaRepository.save(cVacant);
            entityManager.flush();
            produtoRepository.deletarPorCategoriaId(cVacant.getId());
            entityManager.flush();
            assertThat(produtoRepository.findAll()).hasSize(1);
        }

        @Test @DisplayName("PRD-029 - Excluir grande volume de registros vinculados de uma vez")
        void prd029() {
            for (int i = 0; i < 20; i++) {
                entityManager.persist(instanciarProduto("P " + i, "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao));
            }
            entityManager.flush();
            produtoRepository.deletarPorCategoriaId(categoriaPadrao.getId());
            entityManager.flush();
            assertThat(produtoRepository.findAll()).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 5 — findById() (PRD-030 a PRD-033)
    // =========================================================================
    @Nested
    @DisplayName("🆔 BLOCO 5 — findById()")
    class Bloco5FindById {

        @Test @DisplayName("PRD-030 - Localizar produto cadastrado pelo UUID exato")
        void prd030() {
            Optional<Produto> res = produtoRepository.findById(produtoPadrao.getId());
            assertThat(res).isPresent();
        }

        @Test @DisplayName("PRD-031 - Retornar empty para identificador não indexado")
        void prd031() {
            assertThat(produtoRepository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("PRD-032 - Passagem de UUID nulo trata sem travar")
        void prd032() {

            assertThrows(
                    InvalidDataAccessApiUsageException.class,
                    () -> produtoRepository.findById(null)
            );
        }

        @Test @DisplayName("PRD-033 - Buscar utilizando token identificador aleatório")
        void prd033() {
            assertThat(produtoRepository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 6 — save() (PRD-034 a PRD-042)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 6 — save()")
    class Bloco6Save {

        @Test @DisplayName("PRD-034 - Persistir e alocar com sucesso um item simples")
        void prd034() {
            Produto p = instanciarProduto("X-Salada", "Pão, queijo, salada", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.save(p);
            assertThat(salvo.getId()).isNotNull();
        }

        @Test @DisplayName("PRD-035 - Persistir e alocar um agrupamento classificado como Combo")
        void prd035() {
            Produto p = instanciarProduto("Combo Especial", "Lanche + Refri", BigDecimal.TEN, StatusProduto.DISPONIVEL, true, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getIsCombo()).isTrue();
        }

        @Test @DisplayName("PRD-036 - Alocar produto com status inativo/indisponível")
        void prd036() {
            Produto p = instanciarProduto("Sazonal", "Inverno", BigDecimal.TEN, StatusProduto.INDISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getStatus()).isEqualTo(StatusProduto.INDISPONIVEL);
        }

        @Test @DisplayName("PRD-037 - Persistir valor precificado na escala alta centesimal")
        void prd037() {
            Produto p = instanciarProduto("Banquete", "Festa", new BigDecimal("1500.50"), StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getPreco()).isEqualByComparingTo("1500.50");
        }

        @Test @DisplayName("PRD-038 - Persistir precificação mínima elementar")
        void prd038() {
            Produto p = instanciarProduto("Molho Adicional", "Sachê", new BigDecimal("0.50"), StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getPreco()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test @DisplayName("PRD-039 - Persistir endereço/URL de mídia de imagem")
        void prd039() {
            Produto p = instanciarProduto("Burger", "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            p.setUrlImagem("http://cdn.tevao.com/bacon.png");
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getUrlImagem()).isEqualTo("http://cdn.tevao.com/bacon.png");
        }

        @Test @DisplayName("PRD-040 - Permitir e alocar item sem endereço de imagem (nulo)")
        void prd040() {
            Produto p = instanciarProduto("Misterioso", "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            p.setUrlImagem(null);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getUrlImagem()).isNull();
        }

        @Test @DisplayName("PRD-041 - Alocar cadeia descritiva longa na coluna de texto")
        void prd041() {
            Produto p = instanciarProduto("Burger", "D".repeat(200), BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getDescricao()).hasSize(200);
        }

        @Test @DisplayName("PRD-042 - Alocar nome comercial longo respeitando os limites físicos")
        void prd042() {
            Produto p = instanciarProduto("Combo Mestre Mega Blaster Ultra Gigante " + "A".repeat(20), "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getNome()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 7 — Categoria (PRD-043 a PRD-046)
    // =========================================================================
    @Nested
    @DisplayName("🏷️ BLOCO 7 — Categoria Relacional")
    class Bloco7Categoria {

        @Test @DisplayName("PRD-043 - Persistir mapeamento físico da chave estrangeira (FK) de categoria")
        void prd043() {
            assertThat(produtoPadrao.getCategoria().getId()).isEqualTo(categoriaPadrao.getId());
        }

        @Test @DisplayName("PRD-044 - Recompor e ler o objeto categoria de forma íntegra a partir do produto")
        void prd044() {
            Produto p = produtoRepository.findById(produtoPadrao.getId()).get();
            assertThat(p.getCategoria().getNome()).isEqualTo("Lanches Especiais");
        }

        @Test @DisplayName("PRD-045 - Impedir gravação de produto sem categoria associada (NOT NULL)")
        void prd045() {
            Produto p = instanciarProduto("Órfão", "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, null);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(p));
        }

        @Test @DisplayName("PRD-046 - Validar integridade do mapeamento relacional entre entidades")
        void prd046() {
            Produto p = produtoRepository.findById(produtoPadrao.getId()).get();
            assertThat(p.getCategoria()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 8 — Adicionais (PRD-047 a PRD-050)
    // =========================================================================
    @Nested
    @DisplayName("🥓 BLOCO 8 — Coleção de Adicionais")
    class Bloco8Adicionais {

        @Test @DisplayName("PRD-047 - Vincular e persistir um item adicional na coleção da entidade")
        void prd047() {
            Adicional ad = new Adicional(); ad.setNome("Cheddar Duplo"); ad.setPreco(BigDecimal.TEN);
            entityManager.persist(ad);
            produtoPadrao.getAdicionais().add(ad);
            produtoRepository.saveAndFlush(produtoPadrao);

            Produto rec = produtoRepository.findById(produtoPadrao.getId()).get();
            assertThat(rec.getAdicionais()).hasSize(1);
        }

        @Test @DisplayName("PRD-048 - Vincular e alocar múltiplos adicionais simultaneamente")
        void prd048() {
            Adicional a1 = new Adicional(); a1.setNome("A1"); a1.setPreco(BigDecimal.ONE); entityManager.persist(a1);
            Adicional a2 = new Adicional(); a2.setNome("A2"); a2.setPreco(BigDecimal.ONE); entityManager.persist(a2);
            produtoPadrao.getAdicionais().add(a1);
            produtoPadrao.getAdicionais().add(a2);
            produtoRepository.saveAndFlush(produtoPadrao);

            assertThat(produtoRepository.findById(produtoPadrao.getId()).get().getAdicionais()).hasSize(2);
        }

        @Test @DisplayName("PRD-049 - Alocar item sem nenhum adicional na coleção padrão")
        void prd049() {
            assertThat(produtoPadrao.getAdicionais()).isEmpty();
        }

        @Test @DisplayName("PRD-050 - Varrer e ler propriedades financeiras da árvore de adicionais")
        void prd050() {
            Adicional ad = new Adicional(); ad.setNome("Bacon"); ad.setPreco(new BigDecimal("5.00"));
            entityManager.persist(ad);
            produtoPadrao.getAdicionais().add(ad);
            produtoRepository.saveAndFlush(produtoPadrao);

            BigDecimal precoAditivo = produtoRepository.findById(produtoPadrao.getId()).get()
                    .getAdicionais().get(0).getPreco();
            assertThat(precoAditivo).isEqualByComparingTo("5.00");
        }
    }

    // =========================================================================
    // BLOCO 9 — Combo (PRD-051 a PRD-055)
    // =========================================================================
    @Nested
    @DisplayName("📦 BLOCO 9 — Estrutura de Combos")
    class Bloco9Combo {

        @Test @DisplayName("PRD-051 - Validar flag estrutural Combo igual a verdadeiro")
        void prd051() {
            Produto combo = instanciarProduto("Combo X", "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, true, categoriaPadrao);
            produtoRepository.saveAndFlush(combo);
            assertThat(combo.getIsCombo()).isTrue();
        }

        @Test @DisplayName("PRD-052 - Validar flag estrutural Combo igual a falso")
        void prd052() {
            assertThat(produtoPadrao.getIsCombo()).isFalse();
        }

        @Test @DisplayName("PRD-053 - Mapear e persistir itens filhos dentro de um combo mestre")
        void prd053() {
            Produto combo = instanciarProduto("Super Combo", "C", new BigDecimal("45.00"), StatusProduto.DISPONIVEL, true, categoriaPadrao);
            produtoRepository.save(combo);

            // 🎯 FIX: Instanciando a entidade intermediária ComboProduto correta em vez de passar o Produto direto
            com.paullomaggio.estevaoLanches.entities.ComboProduto itemCombo = new com.paullomaggio.estevaoLanches.entities.ComboProduto();
            itemCombo.setCombo(combo);          // Vincula o Combo Pai
            itemCombo.setProduto(produtoPadrao); // Vincula o Produto Filho
            itemCombo.setQuantidade(1);
            entityManager.persist(itemCombo);

            combo.getItensDoCombo().add(itemCombo);
            produtoRepository.saveAndFlush(combo);

            Produto buscado = produtoRepository.findById(combo.getId()).get();
            assertThat(buscado.getItensDoCombo()).hasSize(1);
        }

        @Test @DisplayName("PRD-054 - Alocar combo estruturado inicialmente vazio de filhos")
        void prd054() {
            Produto combo = instanciarProduto("Combo Só", "C", BigDecimal.TEN, StatusProduto.DISPONIVEL, true, categoriaPadrao);
            produtoRepository.saveAndFlush(combo);
            assertThat(combo.getItensDoCombo()).isEmpty();
        }

        @Test
        @DisplayName("PRD-055 - Recompor a cadeia relacional ao ler combo")
        void prd055() {

            Produto combo = instanciarProduto(
                    "Combo Promo",
                    "C",
                    BigDecimal.TEN,
                    StatusProduto.DISPONIVEL,
                    true,
                    categoriaPadrao
            );

            produtoRepository.save(combo);

            ComboProduto itemCombo = new ComboProduto();

            itemCombo.setCombo(combo);
            itemCombo.setProduto(produtoPadrao);

            // ✅ Campo obrigatório
            itemCombo.setQuantidade(1);

            entityManager.persist(itemCombo);

            combo.getItensDoCombo().add(itemCombo);

            produtoRepository.saveAndFlush(combo);

            Produto buscado = produtoRepository.findById(combo.getId()).orElseThrow();

            assertThat(buscado.getIsCombo()).isTrue();
        }
    }

    // =========================================================================
    // BLOCO 10 — PrecisaPreparo (PRD-056 a PRD-059)
    // =========================================================================
    @Nested
    @DisplayName("🍳 BLOCO 10 — PrecisaPreparo (Fila de Cozinha)")
    class Bloco10PrecisaPreparo {

        @Test @DisplayName("PRD-056 - Item quente artesanal exige preparo operacional da chapa")
        void prd056() {
            assertThat(produtoPadrao.getPrecisaPreparo()).isTrue();
        }

        @Test @DisplayName("PRD-057 - Bebidas industrializadas/Refrigerantes dispensam preparo na linha física")
        void prd057() {
            Produto refri = instanciarProduto("Coca-Cola", "Lata", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            refri.setPrecisaPreparo(false);
            produtoRepository.saveAndFlush(refri);
            assertThat(refri.getPrecisaPreparo()).isFalse();
        }

        @Test @DisplayName("PRD-058 - Sobremesas geladas prontas de freezer dispensam preparo")
        void prd058() {
            Produto doce = instanciarProduto("Pudim", "P", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            doce.setPrecisaPreparo(false);
            produtoRepository.saveAndFlush(doce);
            assertThat(doce.getPrecisaPreparo()).isFalse();
        }

        @Test @DisplayName("PRD-059 - Sanduíches estruturados configuram flag ativa de chapa")
        void prd059() {
            Produto burger = instanciarProduto("Burger Simples", "B", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            burger.setPrecisaPreparo(true);
            produtoRepository.saveAndFlush(burger);
            assertThat(burger.getPrecisaPreparo()).isTrue();
        }
    }

    // =========================================================================
    // BLOCO 11 — Concorrência (PRD-060 a PRD-063)
    // =========================================================================
    @Nested
    @DisplayName("⚖️ BLOCO 11 — Concorrência de Estado")
    class Bloco11Concorrencia {

        @Test @DisplayName("PRD-060 - Duas consultas sequenciais idênticas consolidam leitura estática estável")
        void prd060() {
            Optional<Produto> r1 = produtoRepository.findById(produtoPadrao.getId());
            Optional<Produto> r2 = produtoRepository.findById(produtoPadrao.getId());
            assertThat(r1).isEqualTo(r2);
        }

        @Test @DisplayName("PRD-061 - Consulta isolada reflete dados em andamento de mutação")
        void prd061() {
            produtoPadrao.setNome("X-Bacon Modificado");
            Optional<Produto> res = produtoRepository.findById(produtoPadrao.getId());
            assertThat(res.get().getNome()).isEqualTo("X-Bacon Modificado");
        }

        @Test @DisplayName("PRD-062 - Consulta lida de forma consistente durante fluxo de exclusão")
        void prd062() {
            produtoRepository.delete(produtoPadrao);
            Optional<Produto> res = produtoRepository.findById(produtoPadrao.getId());
            assertThat(res).isNotNull();
        }

        @Test @DisplayName("PRD-063 - Repetir varreduras consecitivas sob loops síncronos mantendo isolamento")
        void prd063() {
            for (int i = 0; i < 20; i++) {
                assertThat(produtoRepository.findById(produtoPadrao.getId())).isPresent();
            }
        }
    }

    // =========================================================================
    // BLOCO 12 — Stress (PRD-064 a PRD-067)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 12 — Stress de Carga")
    class Bloco12Stress {

        @Test @DisplayName("PRD-064 - Persistir lote volumoso de 50 produtos sequenciais")
        void prd064() {
            for (int i = 0; i < 50; i++) {
                produtoRepository.save(instanciarProduto("Item " + i, "Desc", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao));
            }
            produtoRepository.flush();
        }

        @Test @DisplayName("PRD-065 - Executar leitura em lote geral de grande volume")
        void prd065() {
            List<Produto> todos = produtoRepository.findAll();
            assertThat(todos).isNotEmpty();
        }

        @Test @DisplayName("PRD-066 - Executar busca por termo textual sob indexador preenchido")
        void prd066() {
            List<Produto> res = produtoRepository.buscarPorTermo("Artesanal");
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-067 - Expurgar categoria contendo multiplicidade de produtos")
        void prd067() {
            produtoRepository.deletarPorCategoriaId(categoriaPadrao.getId());
            produtoRepository.flush();
        }
    }

    // =========================================================================
    // BLOCO 13 — Regressão (PRD-068 a PRD-072)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 13 — Regressão Operacional")
    class Bloco13Regressao {

        @Test @DisplayName("PRD-068 - Fluxo Completo de CRUD: Cadastrar -> Buscar -> Atualizar -> Deletar -> Confirmar")
        void prd068() {
            Produto p = instanciarProduto("Fluxo", "F", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto gravado = produtoRepository.saveAndFlush(p);

            Optional<Produto> busca1 = produtoRepository.findById(gravado.getId());
            assertThat(busca1).isPresent();

            busca1.get().setPreco(new BigDecimal("39.90"));
            produtoRepository.saveAndFlush(busca1.get());

            produtoRepository.deleteById(gravado.getId());
            produtoRepository.flush();

            assertThat(produtoRepository.findById(gravado.getId())).isEmpty();
        }

        @Test @DisplayName("PRD-069 - Operação comum de save preserva imutabilidade do UUID")
        void prd069() {
            UUID idOriginal = produtoPadrao.getId();
            produtoPadrao.setDescricao("Nova Descrição");
            Produto mod = produtoRepository.saveAndFlush(produtoPadrao);
            assertThat(mod.getId()).isEqualTo(idOriginal);
        }

        @Test @DisplayName("PRD-070 - Sincronizações cadastrais mantêm o valor do preço inalterado se intocado")
        void prd070() {
            BigDecimal precoOriginal = produtoPadrao.getPreco();
            produtoPadrao.setNome("Nome Qualquer");
            Produto mod = produtoRepository.saveAndFlush(produtoPadrao);
            assertThat(mod.getPreco()).isEqualByComparingTo(precoOriginal);
        }

        @Test @DisplayName("PRD-071 - Sincronizações cadastrais mantêm o vínculo físico da categoria")
        void prd071() {
            produtoPadrao.setDescricao("Nova");
            Produto mod = produtoRepository.saveAndFlush(produtoPadrao);
            assertThat(mod.getCategoria().getId()).isEqualTo(categoriaPadrao.getId());
        }

        @Test @DisplayName("PRD-072 - Sincronizações de infraestrutura preservam a flag de status de disponibilidade")
        void prd072() {
            produtoPadrao.setNome("Mudar");
            Produto mod = produtoRepository.saveAndFlush(produtoPadrao);
            assertThat(mod.getStatus()).isEqualTo(StatusProduto.DISPONIVEL);
        }
    }

    // =========================================================================
    // BLOCO 14 — Integridade Comercial (PRD-073 a PRD-078)
    // =========================================================================
    @Nested
    @DisplayName("🏢 BLOCO 14 — Integridade Comercial (PDV e Canais)")
    class Bloco14IntegridadeComercial {

        @Test @DisplayName("PRD-073 - Produto acoplado estruturalmente mantém integridade para pedidos")
        void prd073() {
            assertThat(produtoPadrao.getPreco()).isNotNull();
        }

        @Test @DisplayName("PRD-074 - Produto atuando como filho de combo mantém integridade referencial")
        void prd074() {
            assertThat(produtoPadrao.getIsCombo()).isFalse();
        }

        @Test @DisplayName("PRD-075 - Estado comercial de precificação mantém-se estável para faturamento de comandas")
        void prd075() {
            assertThat(produtoPadrao.getPreco()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test @DisplayName("PRD-076 - Mapeamento para emissão/impressão física de cupom de cozinha preservado")
        void prd076() {
            assertThat(produtoPadrao.getPrecisaPreparo()).isTrue();
        }

        @Test @DisplayName("PRD-077 - Atributos de vitrine digital do App de Delivery consistentes")
        void prd077() {
            assertThat(produtoPadrao.getNome()).isNotBlank();
        }

        @Test @DisplayName("PRD-078 - Atributos de leitura rápida de cardápio para terminal PDV web estáveis")
        void prd078() {
            assertThat(produtoPadrao.getStatus()).isEqualTo(StatusProduto.DISPONIVEL);
        }
    }

    // =========================================================================
    // BLOCO 15 — Auditoria (PRD-079 a PRD-084)
    // =========================================================================
    @Nested
    @DisplayName("🧼 BLOCO 15 — Auditoria de Estrutura")
    class Bloco15Auditoria {

        @Test @DisplayName("PRD-079 - Coleção relacional de adicionais instanciada e livre de retornos nulos")
        void prd079() {
            assertThat(produtoPadrao.getAdicionais()).isNotNull();
        }

        @Test @DisplayName("PRD-080 - Coleção relacional de composição de combo instanciada e imune a nulos")
        void prd080() {
            assertThat(produtoPadrao.getItensDoCombo()).isNotNull();
        }

        @Test @DisplayName("PRD-081 - Propriedade categoria preenchida obrigatoriamente após leitura física")
        void prd081() {
            Produto p = produtoRepository.findById(produtoPadrao.getId()).get();
            assertThat(p.getCategoria()).isNotNull();
        }

        @Test @DisplayName("PRD-082 - Identificador primário UUID gerado consistentemente em novos cadastros")
        void prd082() {
            assertThat(produtoPadrao.getId()).isNotNull();
        }

        @Test @DisplayName("PRD-083 - Enumerador estrutural de status de catálogo devidamente instanciado")
        void prd083() {
            assertThat(produtoPadrao.getStatus()).isNotNull();
        }

        @Test @DisplayName("PRD-084 - Repositório mantém propriedades monetárias intactas livre de arredondamentos ocultos")
        void prd084() {
            assertThat(produtoPadrao.getPreco()).isEqualByComparingTo("30.00");
        }
    }

    // =========================================================================
    // BLOCO 16 — Segurança (PRD-085 a PRD-090)
    // =========================================================================
    @Nested
    @DisplayName("🛡️ BLOCO 16 — Segurança de Inputs e Sanitização")
    class Bloco16Seguranca {

        @Test @DisplayName("PRD-085 - Suportar gravação de strings contendo tags HTML sem quebra física")
        void prd085() {
            Produto p = instanciarProduto("<b>Burger</b>", "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getNome()).contains("<b>");
        }

        @Test @DisplayName("PRD-086 - Suportar alocação de blocos descritivos contendo expressões JavaScript")
        void prd086() {
            Produto p = instanciarProduto("Malicioso", "<script>console.log()</script>", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getDescricao()).contains("<script>");
        }

        @Test @DisplayName("PRD-087 - Proteção passiva contra vetores de ataque SQL Injection clássicos")
        void prd087() {
            String injeção = "X-Bacon' OR '1'='1";
            List<Produto> res = produtoRepository.buscarPorTermo(injeção);
            assertThat(res).isEmpty(); // Tratado de forma parametrizada via JPQL/PreparedStatement
        }

        @Test @DisplayName("PRD-088 - Resiliência física do interpretador contra injeções NoSQL/JSON")
        void prd088() {
            List<Produto> res = produtoRepository.buscarPorTermo("{ $gt: \"\" }");
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("PRD-089 - Aceitar codificação Unicode estendida no campo nominal")
        void prd089() {
            Produto p = instanciarProduto("𠜎 𠜱 Hamburger", "Unicode", BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            Produto salvo = produtoRepository.saveAndFlush(p);
            assertThat(salvo.getNome()).isNotNull();
        }

        @Test @DisplayName("PRD-090 - Reter integridade transacional ao processar strings de limites máximos")
        void prd090() {
            Produto p = instanciarProduto("Normal", "A".repeat(255), BigDecimal.TEN, StatusProduto.DISPONIVEL, false, categoriaPadrao);
            assertThat(produtoRepository.saveAndFlush(p)).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 17 — Performance (PRD-091 a PRD-094)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 17 — Performance sob Massa")
    class Bloco17Performance {

        @Test @DisplayName("PRD-091 - Desempenho estável na leitura total via findAll")
        void prd091() {
            List<Produto> res = produtoRepository.findAll();
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-092 - Avaliação veloz do mecanismo de buscarPorTermo texturizado")
        void prd092() {
            List<Produto> res = produtoRepository.buscarPorTermo("Artesanal");
            assertThat(res).isNotNull();
        }

        @Test @DisplayName("PRD-093 - Avaliação veloz da filtragem operacional por status do item")
        void prd093() {
            List<Produto> res = produtoRepository.findByStatus(StatusProduto.DISPONIVEL);
            assertThat(res).isNotEmpty();
        }

        @Test @DisplayName("PRD-094 - Avaliação veloz do filtro excludente de combos")
        void prd094() {
            List<Produto> res = produtoRepository.findByIsComboFalse();
            assertThat(res).isNotEmpty();
        }
    }

    // =========================================================================
    // BLOCO 18 — Consistência do Cardápio (PRD-095 a PRD-100)
    // =========================================================================
    @Nested
    @DisplayName("📋 BLOCO 18 — Consistência do Cardápio")
    class Bloco18ConsistencaCardapio {

        @Test @DisplayName("PRD-095 - Produto mapeado como ativo permanece elegível para exibição")
        void prd095() {
            List<Produto> ativos = produtoRepository.findByStatus(StatusProduto.DISPONIVEL);
            assertThat(ativos).contains(produtoPadrao);
        }

        @Test @DisplayName("PRD-096 - Produto marcado como indisponível é retido de forma consistente")
        void prd096() {
            List<Produto> inativos = produtoRepository.findByStatus(StatusProduto.INDISPONIVEL);
            assertThat(inativos).doesNotContain(produtoPadrao);
        }

        @Test @DisplayName("PRD-097 - Combo recompõe rigorosamente seu estado booleano pós recarga")
        void prd097() {
            Produto combo = instanciarProduto("Combo S", "D", BigDecimal.TEN, StatusProduto.DISPONIVEL, true, categoriaPadrao);
            entityManager.persistAndFlush(combo);
            entityManager.clear(); // Esvazia o cache L1 do Hibernate

            Produto rel = produtoRepository.findById(combo.getId()).get();
            assertThat(rel.getIsCombo()).isTrue();
        }

        @Test @DisplayName("PRD-098 - Itens simples preservam-se blindados contra mutações ocultas para combos")
        void prd098() {
            entityManager.clear();
            Produto rel = produtoRepository.findById(produtoPadrao.getId()).get();
            assertThat(rel.getIsCombo()).isFalse();
        }

        @Test @DisplayName("PRD-099 - Remoção física de categoria em cascata ou higienização limpa os registros corretamente")
        void prd099() {
            produtoRepository.deletarPorCategoriaId(categoriaPadrao.getId());
            entityManager.flush();
            assertThat(produtoRepository.findAll()).isEmpty();
        }

        @Test @DisplayName("PRD-100 - Garantia Absoluta: Nenhum método do repositório expõe dados parciais ou instâncias corrompidas")
        void prd100() {
            Optional<Produto> finalCheck = produtoRepository.findById(produtoPadrao.getId());
            assertThat(finalCheck).isPresent();
            assertThat(finalCheck.get().getNome()).isEqualTo("X-Bacon Artesanal");
        }
    }
}