package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.ClienteRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Avançada — Matriz de Blindagem do Carrinho de Compras")
class CarrinhoServiceTest {

    @Mock private CarrinhoRepository carrinhoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private AdicionalRepository adicionalRepository; // ADICIONADO: Mock de AdicionalRepository

    // Removido @InjectMocks
    private CarrinhoService carrinhoService;

    private UUID clienteId;
    private UUID produtoId;
    private Cliente clientePadrao;
    private Produto produtoPadrao;
    private Carrinho carrinhoExistentePadrao;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks
        // MODIFICADO: Passando adicionalRepository ao construtor
        carrinhoService = new CarrinhoService(carrinhoRepository, clienteRepository, produtoRepository, adicionalRepository);

        clienteId = UUID.randomUUID();
        produtoId = UUID.randomUUID();

        clientePadrao = new Cliente();
        clientePadrao.setId(clienteId);
        clientePadrao.setNome("ESTEVAO CLIENTE");

        produtoPadrao = new Produto();
        produtoPadrao.setId(produtoId);
        produtoPadrao.setNome("BURGER ARTESANAL");
        produtoPadrao.setPreco(new BigDecimal("35.00"));

        carrinhoExistentePadrao = new Carrinho();
        carrinhoExistentePadrao.setId(UUID.randomUUID());
        carrinhoExistentePadrao.setCliente(clientePadrao);
        carrinhoExistentePadrao.setItens(new ArrayList<>());
    }

    // =========================================================================
    // BLOCO 1 — adicionarItem() FLUXO PRINCIPAL
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — adicionarItem() Principal")
    class AdicionarItemPrincipalTests {

        @Test
        @DisplayName("Cenários 1, 3, 4, 5, 6, 7, 8, 9 e 10 — Adicionar item em carrinho existente com sucesso completo")
        void deveAdicionarItemEmCarrinhoExistente() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 2, "Sem cebola", Set.of());

            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            CarrinhoResponseDTO resultado = carrinhoService.adicionarItem(clienteId, dto);

            assertThat(resultado).isNotNull();
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(1);

            ItemCarrinho itemInserido = carrinhoExistentePadrao.getItens().get(0);
            assertThat(itemInserido.getProduto()).isEqualTo(produtoPadrao);
            assertThat(itemInserido.getQuantidade()).isEqualTo(2);
            assertThat(itemInserido.getObservacao()).isEqualTo("Sem cebola");
            assertThat(itemInserido.getCarrinho()).isEqualTo(carrinhoExistentePadrao);

            verify(carrinhoRepository, times(1)).save(carrinhoExistentePadrao);
        }

        @Test
        @DisplayName("Cenário 2 — Deve instanciar e inicializar um novo carrinho se o cliente não possuir nenhum ativo")
        void deveCriarNovoCarrinhoSeNaoExistir() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "Caprichar no molho", Set.of());

            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clientePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            CarrinhoResponseDTO resultado = carrinhoService.adicionarItem(clienteId, dto);

            assertThat(resultado).isNotNull();
            verify(clienteRepository, times(1)).findById(clienteId);
            verify(carrinhoRepository, times(1)).save(any(Carrinho.class));
        }
    }

    // =========================================================================
    // BLOCO 2 — CLIENTE VALIDATION
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Validação de Clientes")
    class ClienteValidationTests {

        @Test
        @DisplayName("Cenário 11 e 13 — Deve prosseguir e invocar findById uma vez quando cliente existir no fluxo de novo carrinho")
        void deveChamarFindByIdUmaVezParaClienteExistente() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clientePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            verify(clienteRepository, times(1)).findById(clienteId);
        }

        @Test
        @DisplayName("Cenário 12 e 14 — Deve lançar RuntimeException e reter criação se o cliente for inexistente")
        void deveLancarExceptionEImpedirCriacaoQuandoClienteNaoExistir() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> // MODIFICADO: Tipo de exceção
                    carrinhoService.adicionarItem(clienteId, dto)
            );

            assertThat(ex.getMessage()).isEqualTo("Cliente não encontrado!");
            verify(produtoRepository, never()).findById(any());
            verify(carrinhoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 3 — PRODUTO VALIDATION
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Validação de Produtos")
    class ProdutoValidationTests {

        @Test
        @DisplayName("Cenário 15 e 17 — Deve prosseguir e acionar findById uma única vez se o produto constar na base")
        void deveBuscarProdutoUmaVez() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            verify(produtoRepository, times(1)).findById(produtoId);
        }

        @Test
        @DisplayName("Cenário 16 e 18 — Deve lançar RuntimeException e abortar save caso o produto informado seja inexistente")
        void deveLancarExceptionEImpedirEscritaQuandoProdutoNaoExistir() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> // MODIFICADO: Tipo de exceção
                    carrinhoService.adicionarItem(clienteId, dto)
            );

            assertThat(ex.getMessage()).isEqualTo("Produto não encontrado!");
            verify(carrinhoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 4 & 5 — ESTADO INTERNO DO TURNAROUND DE CARRINHOS
    // =========================================================================
    @Nested
    @DisplayName("4 & 5. Camada de Blindagem — Ciclo de Vida do Carrinho")
    class CicloDeVidaCarrinhoTests {

        @Test
        @DisplayName("Cenários 19 ao 23 — Reutilização: Deve manter ID, cliente original e apenas apensar novo item")
        void deveReutilizarEPreservarMetadadosDoCarrinhoExistente() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 5, "", Set.of());
            UUID idOriginal = carrinhoExistentePadrao.getId();

            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            assertThat(carrinhoExistentePadrao.getId()).isEqualTo(idOriginal);
            assertThat(carrinhoExistentePadrao.getCliente()).isEqualTo(clientePadrao);
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(1);
        }

        @Test
        @DisplayName("Cenários 24 ao 28 — Inicialização: Novo carrinho deve nascer vazio, associar cliente e conter 1 item pós-fluxo")
        void deveInicializarEConfigurarNovoCarrinhoCorretamente() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(clientePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            verify(carrinhoRepository, times(1)).save(argThat(novoCarrinho ->
                    novoCarrinho.getId() == null &&
                            novoCarrinho.getCliente().equals(clientePadrao) &&
                            novoCarrinho.getItens().size() == 1
            ));
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — MAPEAMENTO DO ITEM E INTEGRALIDADE BIDIRECIONAL
    // =========================================================================
    @Nested
    @DisplayName("6 & 7. Camada de Blindagem — ItemCarrinho e Coesão Bidirecional")
    class ItemCarrinhoIntegridadeTests {

        @Test
        @DisplayName("Cenários 29 ao 33, 35, 36 e 38 — Deve amarrar as pontas do mapeamento e garantir consistência bidirecional de chaves")
        void deveGarantirMapeamentoEConexaoBidirecional() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 3, "Observacao XP", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            ItemCarrinho item = carrinhoExistentePadrao.getItens().get(0);
            assertThat(item.getProduto()).isEqualTo(produtoPadrao);
            assertThat(item.getQuantidade()).isEqualTo(3);
            assertThat(item.getObservacao()).isEqualTo("Observacao XP");
            assertThat(item.getCarrinho()).isEqualTo(carrinhoExistentePadrao); // Dono mapeado
            assertThat(carrinhoExistentePadrao.getItens()).contains(item);   // Lista atualizada
        }

        @Test
        @DisplayName("Cenário 34 e 37 — Preservação: Adicionar um novo item nunca deve expurgar ou limpar a malha de itens históricos")
        void naoDevePerderItensAntigosAoAdicionarNovo() {
            // Criar um produto com ID para o item histórico para evitar NPE
            Produto produtoHistorico = new Produto();
            produtoHistorico.setId(UUID.randomUUID()); // Adicionado ID para o produto histórico
            produtoHistorico.setNome("PRODUTO HISTORICO");
            produtoHistorico.setPreco(new BigDecimal("10.00"));

            ItemCarrinho itemHistorico = new ItemCarrinho();
            itemHistorico.setProduto(produtoHistorico);
            itemHistorico.setQuantidade(1);
            carrinhoExistentePadrao.getItens().add(itemHistorico);

            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 2, "", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            // REMOVIDO: when(produtoRepository.findById(produtoHistorico.getId())).thenReturn(Optional.of(produtoHistorico)); // Este stub é desnecessário
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            // Agora, com a lógica de consolidação, se o produtoId do dto for diferente do produtoHistorico.getId(),
            // teremos 2 itens. Se for o mesmo, teremos 1 item consolidado.
            // O produtoPadrao já tem um ID diferente do produtoHistorico.getId() que acabamos de criar.
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2); // Espera 2 itens, pois os produtos são diferentes
            assertThat(carrinhoExistentePadrao.getItens().get(0)).isEqualTo(itemHistorico); // O item histórico ainda está lá
            assertThat(carrinhoExistentePadrao.getItens().get(1).getProduto()).isEqualTo(produtoPadrao); // O novo item foi adicionado
        }
    }

    // =========================================================================
    // BLOCO 8 — CASOS LIMITE (QUANTIDADES E TEXTOS EXTREMOS)
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Fronteiras e Casos Limite")
    class CasosLimiteTests {

        @BeforeEach
        void setupMocks() {
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("Cenário 39 e 40 — Deve processar com sucesso volumes operacionais normais e massivos (Ex: Qtd 1 e 100)")
        void deveAceitarQuantidadesValidasLimites() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of()));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 100, "", Set.of()));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(1);
            assertThat(carrinhoExistentePadrao.getItens().get(0).getQuantidade()).isEqualTo(101);
        }

        @Test
        @DisplayName("Cenário 41 e 42 — ALERTA DE GAP DE NEGÓCIO: Sistema atual aceita e salva quantidade zero ou valores negativos")
        void deveEvidenciarQueSistemaAceitaQuantidadeZeroENegativa() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 0, "", Set.of()));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, -5, "", Set.of()));

            // 🎯 Provando o comportamento falho aceito atualmente pelo backend
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(1);
            assertThat(carrinhoExistentePadrao.getItens().get(0).getQuantidade()).isEqualTo(-5);
        }

        @Test
        @DisplayName("Cenário 43, 44, 45 e 46 — Deve consolidar observações nulas/vazias e criar novo item para observação diferente") // MODIFICADO: DisplayName
        void deveTolerarVariacoesEstruturaisDeObservacao() {
            String stringGigante = "A".repeat(1000);

            // MODIFICADO: Adicionado Set.of() como quarto argumento em todas as chamadas
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, null, Set.of())); // Observação inicial null
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of())); // Sobrescreve com ""
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "   ", Set.of())); // Sobrescreve com "   "
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, stringGigante, Set.of())); // Adiciona com stringGigante

            // MODIFICADO: Asserções para refletir a nova regra de identidade
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2); // Agora 2 itens

            // Encontra o item com observação normalizada vazia
            Optional<ItemCarrinho> itemVazio = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> "".equals(item.getObservacao()) || item.getObservacao() == null)
                    .findFirst();
            assertThat(itemVazio).isPresent();
            assertThat(itemVazio.get().getQuantidade()).isEqualTo(3); // 1 + 1 + 1 = 3

            // Encontra o item com observação stringGigante
            Optional<ItemCarrinho> itemGigante = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> stringGigante.equals(item.getObservacao()))
                    .findFirst();
            assertThat(itemGigante).isPresent();
            assertThat(itemGigante.get().getQuantidade()).isEqualTo(1);
        }
    }

    // =========================================================================
    // BLOCO 9 — REGRESSÕES OPERACIONAIS DE FLUXO SEQUECIAL
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Fluxos de Regressão Operacional")
    class RegressaoOperacionalTests {

        @BeforeEach
        void setupMocks() {
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("Cenário 47, 49 e 50 — Deve empilhar múltiplos produtos distintos e retornar todos mapeados no DTO")
        void deveAdicionarProdutosDiferentesESequenciais() {
            Produto prodB = new Produto(); prodB.setId(UUID.randomUUID()); prodB.setNome("COCA COLA");

            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(produtoRepository.findById(prodB.getId())).thenReturn(Optional.of(prodB));

            // MODIFICADO: Adicionado Set.of() como quarto argumento
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of()));
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            CarrinhoResponseDTO respostaFinal = carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(prodB.getId(), 2, "Gelo", Set.of()));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
            assertThat(respostaFinal).isNotNull();
        }

        @Test
        @DisplayName("Cenário 48 — Deve criar linhas separadas para o mesmo produto com observações diferentes") // MODIFICADO: DisplayName
        void deveConfirmarDuplicidadeDeLinhaParaMesmoProduto() {
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));

            // MODIFICADO: Adicionado Set.of() como quarto argumento
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 2, "A", Set.of()));
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 3, "B", Set.of()));

            // MODIFICADO: Asserções para refletir a nova regra de identidade
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2); // Agora 2 itens

            Optional<ItemCarrinho> itemA = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> "A".equals(item.getObservacao()))
                    .findFirst();
            assertThat(itemA).isPresent();
            assertThat(itemA.get().getQuantidade()).isEqualTo(2);

            Optional<ItemCarrinho> itemB = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> "B".equals(item.getObservacao()))
                    .findFirst();
            assertThat(itemB).isPresent();
            assertThat(itemB.get().getQuantidade()).isEqualTo(3);
        }
    }

    // =========================================================================
    // BLOCO 10 — ISOLAMENTO E EFEITO COLATERAL ZERO
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Isolamento do Contexto de Memória")
    class IsolamentoContextoTests {

        @Test
        @DisplayName("Cenários 51 ao 55 — Adição nunca deve acionar deleções, trocar ponteiros de entidades ou violar itens antigos")
        void deveGarantirIsolamentoTotalDoAmbiente() {
            ItemCarrinho itemAntigo = new ItemCarrinho();
            itemAntigo.setProduto(produtoPadrao);
            itemAntigo.setQuantidade(10);
            // Observação do item antigo é null por padrão, que normaliza para ""
            carrinhoExistentePadrao.getItens().add(itemAntigo);

            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            // MODIFICADO: Adicionado Set.of() como quarto argumento
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of()));

            // Provas de Isolamento de Estado
            assertThat(carrinhoExistentePadrao.getCliente()).isEqualTo(clientePadrao); // Cliente imutável
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(1); // Deve ter apenas 1 item consolidado
            assertThat(carrinhoExistentePadrao.getItens().get(0).getQuantidade()).isEqualTo(11); // 10 + 1 = 11
            assertThat(carrinhoExistentePadrao.getItens().get(0).getProduto()).isEqualTo(produtoPadrao); // Produto original intacto

            verify(carrinhoRepository, never()).delete(any());
            verify(carrinhoRepository, never()).deleteById(any());
        }
    }

    // =========================================================================
    // BLOCO 11 — PERSISTÊNCIA E CONTAGEM ESTRITA DE CHAMADAS (I/O)
    // =========================================================================
    @Nested
    @DisplayName("11. Camada de Blindagem — Auditoria Síncrona de Chamadas I/O")
    class AuditoriaChamadasTests {

        @Test
        @DisplayName("Cenários 56 ao 60 — Deve monitorar e cravar a volumetria exata de acessos a dados mapeados no método")
        void deveGarantirContagemEstritaDeChamadas() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "", Set.of());
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            // Verificação atômica de volumetria de I/O
            verify(carrinhoRepository, times(1)).findByClienteId(clienteId);
            verify(produtoRepository, times(1)).findById(produtoId);
            verify(carrinhoRepository, times(1)).save(any(Carrinho.class));

            verifyNoInteractions(clienteRepository); // No fluxo feliz de carrinho existente, a tabela cliente não deve ser tocada
            verifyNoInteractions(adicionalRepository); // ADICIONADO: No fluxo sem adicionais, o AdicionalRepository não deve ser tocado
        }
    }

    // =========================================================================
    // BLOCO 12 — SIMULAÇÃO DETERMINÍSTICA DE CONCORRÊNCIA NO BALCÃO/PDV
    // =========================================================================
    @Nested
    @DisplayName("12. Camada de Blindagem — Simulação Concorrente Determinística")
    class ConcorrenciaPDVTests {

        @Test
        @DisplayName("Cenários 61 ao 65 — Simula lançamentos simultâneos com observações diferentes resultando em itens separados") // MODIFICADO: DisplayName
        void simulacaoCorridaDeLancamentosSimultaneos() {
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dtoGarcom1 = new ItemCarrinhoRequestDTO(produtoId, 1, "Garçom 1", Set.of());
            // MODIFICADO: Adicionado Set.of() como quarto argumento
            ItemCarrinhoRequestDTO dtoGarcom2 = new ItemCarrinhoRequestDTO(produtoId, 3, "Garçom 2", Set.of());

            // Configura o comportamento reentrante do banco simulando que ambos leram o mesmo estado original da sacola
            when(carrinhoRepository.findByClienteId(clienteId))
                    .thenReturn(Optional.of(carrinhoExistentePadrao))  // Leitura do Garçom 1
                    .thenReturn(Optional.of(carrinhoExistentePadrao)); // Leitura do Garçom 2 paralelo

            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            // Execução dos fluxos síncronos simulando a concorrência interceptada
            carrinhoService.adicionarItem(clienteId, dtoGarcom1);
            carrinhoService.adicionarItem(clienteId, dtoGarcom2);

            // MODIFICADO: Asserções para refletir a nova regra de identidade
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2); // Agora 2 itens

            Optional<ItemCarrinho> itemGarcom1 = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> "Garçom 1".equals(item.getObservacao()))
                    .findFirst();
            assertThat(itemGarcom1).isPresent();
            assertThat(itemGarcom1.get().getQuantidade()).isEqualTo(1);

            Optional<ItemCarrinho> itemGarcom2 = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> "Garçom 2".equals(item.getObservacao()))
                    .findFirst();
            assertThat(itemGarcom2).isPresent();
            assertThat(itemGarcom2.get().getQuantidade()).isEqualTo(3);

            verify(carrinhoRepository, times(2)).save(any(Carrinho.class));
        }
    }

    // =========================================================================
    // BLOCO 13 — TESTES PARA ADICIONAIS (NOVO)
    // =========================================================================
    @Nested
    @DisplayName("13. Camada de Blindagem — Adicionais")
    class AdicionaisTests {

        private UUID adicionalId1;
        private UUID adicionalId2;
        private Adicional adicional1;
        private Adicional adicional2;

        @BeforeEach
        void setupAdicionais() {
            adicionalId1 = UUID.randomUUID();
            adicionalId2 = UUID.randomUUID();

            adicional1 = new Adicional();
            adicional1.setId(adicionalId1);
            adicional1.setNome("Bacon");
            adicional1.setPreco(new BigDecimal("5.00"));

            adicional2 = new Adicional();
            adicional2.setId(adicionalId2);
            adicional2.setNome("Cheddar");
            adicional2.setPreco(new BigDecimal("4.00"));

            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            // REMOVIDO: when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("Deve consolidar itens com mesmo produto, mesmos adicionais e mesma observação")
        void deveConsolidarItensComMesmaIdentidadeCompleta() {
            Set<UUID> idsAdicionais = Set.of(adicionalId1, adicionalId2);
            when(adicionalRepository.findAllById(idsAdicionais)).thenReturn(List.of(adicional1, adicional2));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0)); // ADICIONADO

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 2, "Com tudo", idsAdicionais));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 3, "Com tudo", idsAdicionais));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(1);
            ItemCarrinho itemConsolidado = carrinhoExistentePadrao.getItens().get(0);
            assertThat(itemConsolidado.getQuantidade()).isEqualTo(5);
            assertThat(itemConsolidado.getObservacao()).isEqualTo("Com tudo");
            assertThat(itemConsolidado.getAdicionais()).containsExactlyInAnyOrder(adicional1, adicional2);
            verify(adicionalRepository, times(2)).findAllById(idsAdicionais); // MODIFICADO: times(1) para times(2)
        }

        @Test
        @DisplayName("Deve criar itens separados para o mesmo produto com adicionais diferentes")
        void deveCriarItensSeparadosParaAdicionaisDiferentes() {
            Set<UUID> idsAdicionais1 = Set.of(adicionalId1);
            Set<UUID> idsAdicionais2 = Set.of(adicionalId2);

            when(adicionalRepository.findAllById(idsAdicionais1)).thenReturn(List.of(adicional1));
            when(adicionalRepository.findAllById(idsAdicionais2)).thenReturn(List.of(adicional2));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0)); // ADICIONADO

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 2, "Sem observacao", idsAdicionais1));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 3, "Sem observacao", idsAdicionais2));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);

            Optional<ItemCarrinho> item1 = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> item.getAdicionais().contains(adicional1))
                    .findFirst();
            assertThat(item1).isPresent();
            assertThat(item1.get().getQuantidade()).isEqualTo(2);
            assertThat(item1.get().getAdicionais()).containsExactly(adicional1);

            Optional<ItemCarrinho> item2 = carrinhoExistentePadrao.getItens().stream()
                    .filter(item -> item.getAdicionais().contains(adicional2))
                    .findFirst();
            assertThat(item2).isPresent();
            assertThat(item2.get().getQuantidade()).isEqualTo(3);
            assertThat(item2.get().getAdicionais()).containsExactly(adicional2);
            verify(adicionalRepository, times(1)).findAllById(idsAdicionais1);
            verify(adicionalRepository, times(1)).findAllById(idsAdicionais2);
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException se um ID de adicional for inexistente")
        void deveLancarExcecaoParaAdicionalInexistente() {
            UUID adicionalInexistenteId = UUID.randomUUID();
            Set<UUID> idsAdicionais = Set.of(adicionalId1, adicionalInexistenteId);

            when(adicionalRepository.findAllById(idsAdicionais)).thenReturn(List.of(adicional1)); // Apenas um encontrado

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> // MODIFICADO: Tipo de exceção
                    carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "", idsAdicionais))
            );

            assertThat(ex.getMessage()).contains("Adicionais não encontrados:");
            assertThat(ex.getMessage()).contains(adicionalInexistenteId.toString());
            verify(adicionalRepository, times(1)).findAllById(idsAdicionais);
            verify(carrinhoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve criar itens separados para o mesmo produto com observações e adicionais diferentes")
        void deveCriarItensSeparadosParaObservacoesEAdicionaisDiferentes() {
            Set<UUID> idsAdicionais1 = Set.of(adicionalId1);
            Set<UUID> idsAdicionais2 = Set.of(adicionalId2);

            when(adicionalRepository.findAllById(idsAdicionais1)).thenReturn(List.of(adicional1));
            when(adicionalRepository.findAllById(idsAdicionais2)).thenReturn(List.of(adicional2));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0)); // ADICIONADO

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "Obs A", idsAdicionais1));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "Obs B", idsAdicionais2));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
        }

        @Test
        @DisplayName("Deve criar itens separados para o mesmo produto com mesmos adicionais mas observações diferentes")
        void deveCriarItensSeparadosParaMesmosAdicionaisMasObservacoesDiferentes() {
            Set<UUID> idsAdicionais = Set.of(adicionalId1);
            when(adicionalRepository.findAllById(idsAdicionais)).thenReturn(List.of(adicional1));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0)); // ADICIONADO

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "Obs A", idsAdicionais));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "Obs B", idsAdicionais));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
        }

        @Test
        @DisplayName("Deve criar itens separados para o mesmo produto com mesma observação mas adicionais diferentes")
        void deveCriarItensSeparadosParaMesmaObservacaoMasAdicionaisDiferentes() {
            Set<UUID> idsAdicionais1 = Set.of(adicionalId1);
            Set<UUID> idsAdicionais2 = Set.of(adicionalId2);

            when(adicionalRepository.findAllById(idsAdicionais1)).thenReturn(List.of(adicional1));
            when(adicionalRepository.findAllById(idsAdicionais2)).thenReturn(List.of(adicional2));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0)); // ADICIONADO

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "Obs Comum", idsAdicionais1));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "Obs Comum", idsAdicionais2));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
        }
    }
}