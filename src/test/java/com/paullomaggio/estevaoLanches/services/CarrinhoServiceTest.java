package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.Produto;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Avançada — Matriz de Blindagem do Carrinho de Compras")
class CarrinhoServiceTest {

    @Mock private CarrinhoRepository carrinhoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ProdutoRepository produtoRepository;

    @InjectMocks private CarrinhoService carrinhoService;

    private UUID clienteId;
    private UUID produtoId;
    private Cliente clientePadrao;
    private Produto produtoPadrao;
    private Carrinho carrinhoExistentePadrao;

    @BeforeEach
    void setUp() {
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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 2, "Sem cebola");

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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "Caprichar no molho");

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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "");
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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "");
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());
            when(clienteRepository.findById(clienteId)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "");
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            verify(produtoRepository, times(1)).findById(produtoId);
        }

        @Test
        @DisplayName("Cenário 16 e 18 — Deve lançar RuntimeException e abortar save caso o produto informado seja inexistente")
        void deveLancarExceptionEImpedirEscritaQuandoProdutoNaoExistir() {
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "");
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 5, "");
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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "");
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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 3, "Observacao XP");
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
            ItemCarrinho itemHistorico = new ItemCarrinho();
            itemHistorico.setProduto(new Produto());
            itemHistorico.setQuantidade(1);
            carrinhoExistentePadrao.getItens().add(itemHistorico);

            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 2, "");
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
            assertThat(carrinhoExistentePadrao.getItens().get(0)).isEqualTo(itemHistorico);
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
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, ""));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 100, ""));

            assertThat(carrinhoExistentePadrao.getItens().get(0).getQuantidade()).isEqualTo(1);
            assertThat(carrinhoExistentePadrao.getItens().get(1).getQuantidade()).isEqualTo(100);
        }

        @Test
        @DisplayName("Cenário 41 e 42 — ALERTA DE GAP DE NEGÓCIO: Sistema atual aceita e salva quantidade zero ou valores negativos")
        void deveEvidenciarQueSistemaAceitaQuantidadeZeroENegativa() {
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 0, ""));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, -5, ""));

            // 🎯 Provando o comportamento falho aceito atualmente pelo backend
            assertThat(carrinhoExistentePadrao.getItens().get(0).getQuantidade()).isZero();
            assertThat(carrinhoExistentePadrao.getItens().get(1).getQuantidade()).isNegative();
        }

        @Test
        @DisplayName("Cenário 43, 44, 45 e 46 — Strings: Deve tolerar observações vazias, nulas, com caracteres de espaço ou strings gigantes")
        void deveTolerarVariacoesEstruturaisDeObservacao() {
            String stringGigante = "A".repeat(1000);

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, null));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, ""));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, "   "));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, stringGigante));

            assertThat(carrinhoExistentePadrao.getItens().get(0).getObservacao()).isNull();
            assertThat(carrinhoExistentePadrao.getItens().get(1).getObservacao()).isEmpty();
            assertThat(carrinhoExistentePadrao.getItens().get(2).getObservacao()).isBlank();
            assertThat(carrinhoExistentePadrao.getItens().get(3).getObservacao().length()).isEqualTo(1000);
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

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, ""));
            CarrinhoResponseDTO respostaFinal = carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(prodB.getId(), 2, "Gelo"));

            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
            assertThat(respostaFinal).isNotNull();
        }

        @Test
        @DisplayName("Cenário 48 — ALERTA DE COMPORTAMENTO: Adicionar o mesmo produto duas vezes duplica a linha em vez de somar quantidades")
        void deveConfirmarDuplicidadeDeLinhaParaMesmoProduto() {
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 2, "A"));
            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 3, "B"));

            // 🎯 Provando que o comportamento atual duplica linhas no banco de dados do PDV
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
            assertThat(carrinhoExistentePadrao.getItens().get(0).getQuantidade()).isEqualTo(2);
            assertThat(carrinhoExistentePadrao.getItens().get(1).getQuantidade()).isEqualTo(3);
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
            carrinhoExistentePadrao.getItens().add(itemAntigo);

            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, new ItemCarrinhoRequestDTO(produtoId, 1, ""));

            // Provas de Isolamento de Estado
            assertThat(carrinhoExistentePadrao.getCliente()).isEqualTo(clientePadrao); // Cliente imutável
            assertThat(carrinhoExistentePadrao.getItens().get(0).getQuantidade()).isEqualTo(10); // Histórico preservado
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
            ItemCarrinhoRequestDTO dto = new ItemCarrinhoRequestDTO(produtoId, 1, "");
            when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinhoExistentePadrao));
            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            carrinhoService.adicionarItem(clienteId, dto);

            // Verificação atômica de volumetria de I/O
            verify(carrinhoRepository, times(1)).findByClienteId(clienteId);
            verify(produtoRepository, times(1)).findById(produtoId);
            verify(carrinhoRepository, times(1)).save(any(Carrinho.class));

            verifyNoInteractions(clienteRepository); // No fluxo feliz de carrinho existente, a tabela cliente não deve ser tocada
        }
    }

    // =========================================================================
    // BLOCO 12 — SIMULAÇÃO DETERMINÍSTICA DE CONCORRÊNCIA NO BALCÃO/PDV
    // =========================================================================
    @Nested
    @DisplayName("12. Camada de Blindagem — Simulação Concorrente Determinística")
    class ConcorrenciaPDVTests {

        @Test
        @DisplayName("Cenários 61 ao 65 — Corrida de Lançamento: Simula dois garçons batendo commits na mesma fração de segundo")
        void simulacaoCorridaDeLancamentosSimultaneos() {
            ItemCarrinhoRequestDTO dtoGarcom1 = new ItemCarrinhoRequestDTO(produtoId, 1, "Garçom 1");
            ItemCarrinhoRequestDTO dtoGarcom2 = new ItemCarrinhoRequestDTO(produtoId, 3, "Garçom 2");

            // Configura o comportamento reentrante do banco simulando que ambos leram o mesmo estado original da sacola
            when(carrinhoRepository.findByClienteId(clienteId))
                    .thenReturn(Optional.of(carrinhoExistentePadrao))  // Leitura do Garçom 1
                    .thenReturn(Optional.of(carrinhoExistentePadrao)); // Leitura do Garçom 2 paralelo

            when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoPadrao));
            when(carrinhoRepository.save(any(Carrinho.class))).thenAnswer(i -> i.getArgument(0));

            // Execução dos fluxos síncronos simulando a concorrência interceptada
            carrinhoService.adicionarItem(clienteId, dtoGarcom1);
            carrinhoService.adicionarItem(clienteId, dtoGarcom2);

            // Bate a prova se o acúmulo em lista foi retido e gravado corretamente sem perdas de ponteiro de memória
            assertThat(carrinhoExistentePadrao.getItens()).hasSize(2);
            assertThat(carrinhoExistentePadrao.getItens().get(0).getObservacao()).isEqualTo("Garçom 1");
            assertThat(carrinhoExistentePadrao.getItens().get(1).getObservacao()).isEqualTo("Garçom 2");

            verify(carrinhoRepository, times(2)).save(any(Carrinho.class));
        }
    }
}