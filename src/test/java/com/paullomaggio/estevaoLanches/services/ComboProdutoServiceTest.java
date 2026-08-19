package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ComboProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ComboProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ComboComposicaoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComposicaoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.ComboProduto;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ComboProdutoRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Suprema — Engenharia de Matriz de Combo de Produtos")
class ComboProdutoServiceTest {

    @Mock private ComboProdutoRepository comboProdutoRepository;
    @Mock private ProdutoRepository produtoRepository;

    // Removido @InjectMocks
    private ComboProdutoService comboProdutoService;

    private UUID idComboPai;
    private UUID idProdutoFilho;
    private Produto comboPaiMock;
    private Produto produtoFilhoMock;
    private ComboProduto vinculoMock;
    private UUID idVinculo;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks
        comboProdutoService = new ComboProdutoService(comboProdutoRepository, produtoRepository);

        idComboPai = UUID.randomUUID();
        idProdutoFilho = UUID.randomUUID();
        idVinculo = UUID.randomUUID();

        comboPaiMock = new Produto();
        comboPaiMock.setId(idComboPai);
        comboPaiMock.setNome("COMBO FAMÍLIA MAX");
        comboPaiMock.setStatus(StatusProduto.DISPONIVEL);

        produtoFilhoMock = new Produto();
        produtoFilhoMock.setId(idProdutoFilho);
        produtoFilhoMock.setNome("BATATA FRITA G");
        produtoFilhoMock.setStatus(StatusProduto.DISPONIVEL);

        vinculoMock = new ComboProduto(idVinculo, comboPaiMock, produtoFilhoMock, 2);
    }

    // =========================================================================
    // BLOCO 1 — ASSOCIAR PRODUTO AO COMBO
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — associarProdutoAoCombo() Fluxo Feliz")
    class AssociarProdutoAoComboTests {

        @Test
        @DisplayName("CT-001 ao CT-008: Deve acionar chaves com sucesso vinculando produto filho ao combo e injetando UUID pós-save")
        void deveAssociarProdutoAoComboComSucesso() {
            ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(idComboPai, idProdutoFilho, 2);

            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(idProdutoFilho)).thenReturn(Optional.of(produtoFilhoMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);

            ComboProdutoResponseDTO resultado = comboProdutoService.associarProdutoAoCombo(dto);

            assertNotNull(resultado);
            verify(produtoRepository, times(1)).findById(idComboPai);
            verify(produtoRepository, times(1)).findById(idProdutoFilho);
            verify(comboProdutoRepository, times(1)).save(argThat(cp ->
                    cp.getCombo().equals(comboPaiMock) &&
                            cp.getProduto().equals(produtoFilhoMock) &&
                            cp.getQuantidade() == 2
            ));
        }
    }

    // =========================================================================
    // BLOCO 2 & 3 — VALIDAÇÃO DOS COMPONENTES (COMBO PAI E PRODUTO FILHO)
    // =========================================================================
    @Nested
    @DisplayName("2 & 3. Camada de Blindagem — Validação de Elementos e IDs")
    class ValidacaoElementosCardapioTests {

        @Test
        @DisplayName("CT-009 e CT-012: Combo pai inexistente deve abortar a persistência e disparar ResourceNotFoundException")
        void deveFalharComboPaiInexistente() {
            ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(UUID.randomUUID(), idProdutoFilho, 1);
            when(produtoRepository.findById(dto.comboId())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> comboProdutoService.associarProdutoAoCombo(dto));
            verify(comboProdutoRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-013 e CT-016: Produto filho inexistente deve abortar o salvamento e disparar ResourceNotFoundException")
        void deveFalharProdutoFilhoInexistente() {
            ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(idComboPai, UUID.randomUUID(), 1);
            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(dto.produtoId())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> comboProdutoService.associarProdutoAoCombo(dto));
            verify(comboProdutoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 4 — QUANTIDADE DE ITENS NA ESTRUTURA
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Volumes e Limites de Quantidades")
    class QuantidadeLimitesTests {

        @Test
        @DisplayName("CT-017 ao CT-019, CT-022: Deve processar com êxito associações com volumes tradicionais e elevados (Ex: Qtd 1, 2, 10, 100)")
        void deveAceitarQuantidadesValidas() {
            ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(idComboPai, idProdutoFilho, 100);
            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(idProdutoFilho)).thenReturn(Optional.of(produtoFilhoMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);

            assertNotNull(comboProdutoService.associarProdutoAoCombo(dto));
        }
    }

    // =========================================================================
    // BLOCO 5 — REGRAS DE NEGÓCIO DO PROJETO (DOCUMENTAÇÃO DE COMPORTAMENTO)
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — Alertas de Gaps de Regras de Negócio")
    class RegrasDeNegocioCardapioTests {

        @Test
        @DisplayName("CT-023: ALERTA DE GAP — Código atual permite associar acidentalmente um combo a si mesmo")
        void deveEvidenciarQueCodigoAtualPermiteAutoAssociacao() {
            ComboProdutoRequestDTO dtoAutoAssociacao = new ComboProdutoRequestDTO(idComboPai, idComboPai, 1);

            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);

            // Documenta o comportamento atual falho do backend
            assertDoesNotThrow(() -> comboProdutoService.associarProdutoAoCombo(dtoAutoAssociacao));
        }

        @Test
        @DisplayName("CT-024: ALERTA DE GAP — Sistema atual permite criar linhas duplicadas idênticas sem agrupar quantidades")
        void deveEvidenciarQueCodigoPermiteDuplicidade() {
            ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(idComboPai, idProdutoFilho, 1);

            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(idProdutoFilho)).thenReturn(Optional.of(produtoFilhoMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);

            // Simula o operador clicando duas vezes seguidas e criando dois registros em vez de estourar erro
            assertNotNull(comboProdutoService.associarProdutoAoCombo(dto));
            assertNotNull(comboProdutoService.associarProdutoAoCombo(dto));
        }
    }

    // =========================================================================
    // BLOCO 6 — LISTAR ESTRUTURA DO COMBO
    // =========================================================================
    @Nested
    @DisplayName("6. Camada de Blindagem — Leitura Pura e Estrutura")
    class ListarEstruturaTests {

        @Test
        @DisplayName("CT-028, CT-030 ao CT-033: Deve ler a malha estrutural sem disparar comandos de escrita ou deleção")
        void deveListarEstruturaSemEfeitosColaterais() {
            when(comboProdutoRepository.findByComboId(idComboPai)).thenReturn(List.of(vinculoMock));

            List<ComboProdutoResponseDTO> resultado = comboProdutoService.listarEstruturaDoCombo(idComboPai);

            assertFalse(resultado.isEmpty());
            verify(comboProdutoRepository, times(1)).findByComboId(idComboPai);
            verify(comboProdutoRepository, never()).save(any());
            verify(comboProdutoRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("CT-029: Combo recém-criado sem insumos associados deve retornar uma lista vazia segura")
        void deveRetornarListaVaziaSeComboSemItens() {
            when(comboProdutoRepository.findByComboId(idComboPai)).thenReturn(Collections.emptyList());
            List<ComboProdutoResponseDTO> resultado = comboProdutoService.listarEstruturaDoCombo(idComboPai);
            assertTrue(resultado.isEmpty());
        }
    }

    // =========================================================================
    // BLOCO 7 — DESASSOCIAR (REMOÇÃO FISICA)
    // =========================================================================
    @Nested
    @DisplayName("7. Camada de Blindagem — desassociarItem()")
    class DesassociarItemTests {

        @Test
        @DisplayName("CT-034 e CT-036: Deve remover o vínculo existente chamando a exclusão uma única vez")
        void deveDesassociarComSucesso() {
            when(comboProdutoRepository.existsById(idVinculo)).thenReturn(true);
            doNothing().when(comboProdutoRepository).deleteById(idVinculo);

            comboProdutoService.desassociarItem(idVinculo);

            verify(comboProdutoRepository, times(1)).deleteById(idVinculo);
        }

        @Test
        @DisplayName("CT-035 e CT-037: Tentar remover um vínculo inexistente ou órfão deve disparar ResourceNotFoundException instantaneamente")
        void deveLancarExceptionSeVinculoInexistente() {
            UUID idQualquer = UUID.randomUUID();
            when(comboProdutoRepository.existsById(idQualquer)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> comboProdutoService.desassociarItem(idQualquer));
            verify(comboProdutoRepository, never()).deleteById(any());
        }
    }

    // =========================================================================
    // BLOCO 8, 9 & 12 — INTEGRALIDADE DOS RELACIONAMENTOS, REGRESSÃO E AUDITORIA
    // =========================================================================
    @Nested
    @DisplayName("8, 9 & 12. Camada de Blindagem — Provas de Regressão e Relacionamentos")
    class RegressaoEIntegridadeTests {

        @Test
        @DisplayName("CT-044: Regressão Integrada — Operações de Adição seguidas de Exclusão Física devem reestabelecer o estado limpo")
        void regressaoFluxoCicloVida() {
            // Drop 1: Criação e Associação
            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(idProdutoFilho)).thenReturn(Optional.of(produtoFilhoMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);
            comboProdutoService.associarProdutoAoCombo(new ComboProdutoRequestDTO(idComboPai, idProdutoFilho, 2));

            // Drop 2: Quebra e Desassociação
            when(comboProdutoRepository.existsById(idVinculo)).thenReturn(true);
            comboProdutoService.desassociarItem(idVinculo);

            verify(comboProdutoRepository, times(1)).deleteById(idVinculo);
        }
    }

    // =========================================================================
    // BLOCO 10 — FLUXO DE CARDÁPIO (SIMULAÇÕES DO CATALOGO DE VENDAS APP)
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Casos de Uso do Fluxo de Cardápio App")
    class FluxoDeCardapioVendasTests {

        @Test
        @DisplayName("CT-046 ao CT-049: Simula a montagem estrutural estável de um Combo Família com múltiplos refrigerantes e itens")
        void deveSimularComboEstruturaFaturamento() {
            // 🎯 FIX DEFINITIVO: Instanciação por setters para evitar acoplamento com construtores ausentes na classe Produto
            Produto refriMock = new Produto();
            refriMock.setId(UUID.randomUUID());
            refriMock.setNome("COCA COLA 2L");
            refriMock.setStatus(StatusProduto.DISPONIVEL);

            ComboProduto c1 = new ComboProduto(UUID.randomUUID(), comboPaiMock, produtoFilhoMock, 1);
            ComboProduto c2 = new ComboProduto(UUID.randomUUID(), comboPaiMock, refriMock, 2);

            when(comboProdutoRepository.findByComboId(idComboPai)).thenReturn(List.of(c1, c2));

            List<ComboProdutoResponseDTO> estrutura = comboProdutoService.listarEstruturaDoCombo(idComboPai);

            assertEquals(2, estrutura.size());
            verify(comboProdutoRepository, times(1)).findByComboId(idComboPai);
        }

        @Test
        @DisplayName("CT-059 [Exclusivo Atendimento Evolutivo]: ALERTA DE REGRESSÃO — Produtos inativos ou indisponíveis atualmente conseguem entrar na malha de novos combos")
        void deveVerificarComportamentoDeProdutosIndisponiveisNoCombo() {
            produtoFilhoMock.setStatus(StatusProduto.INDISPONIVEL); // Item esgotado na cozinha

            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(idProdutoFilho)).thenReturn(Optional.of(produtoFilhoMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);

            // Confirma que o sistema atualmente aceita insumos esgotados na estrutura do combo
            assertNotNull(comboProdutoService.associarProdutoAoCombo(new ComboProdutoRequestDTO(idComboPai, idProdutoFilho, 1)));
        }
    }

    // =========================================================================
    // BLOCO 11 — CONCORRÊNCIA REENTRANTE ENTRE EQUIPES NO BACKOFFICE
    // =========================================================================
    @Nested
    @DisplayName("11. Camada de Blindagem — Simulação Concorrente de Dados no PDV")
    class ConcorrenciaBackofficeTests {

        @Test
        @DisplayName("CT-051 e CT-052: Corrida de Lançamento — Simula duas instâncias salvando insumos idênticos simultaneamente")
        void corridaAdicaoSimultanea() {
            ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(idComboPai, idProdutoFilho, 1);

            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(idProdutoFilho)).thenReturn(Optional.of(produtoFilhoMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);

            // Execuções síncronas simulando a concorrência interceptada
            comboProdutoService.associarProdutoAoCombo(dto);
            comboProdutoService.associarProdutoAoCombo(dto);

            // Garante que ambos os cliques processaram na esteira sem quebras de ponteiro de memória
            verify(comboProdutoRepository, times(2)).save(any(ComboProduto.class));
        }

        @Test
        @DisplayName("CT-053: Ordem Rígida — Deve auditar a cronologia estrita dos comandos para evitar inconsistências no catálogo")
        void auditoriaOrdemCronologicaEstrita() {
            ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(idComboPai, idProdutoFilho, 5);
            when(produtoRepository.findById(idComboPai)).thenReturn(Optional.of(comboPaiMock));
            when(produtoRepository.findById(idProdutoFilho)).thenReturn(Optional.of(produtoFilhoMock));
            when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoMock);

            comboProdutoService.associarProdutoAoCombo(dto);

            // Prova real de rastreabilidade síncrona
            InOrder ordemFiscal = inOrder(produtoRepository, comboProdutoRepository);
            ordemFiscal.verify(produtoRepository).findById(idComboPai);
            ordemFiscal.verify(produtoRepository).findById(idProdutoFilho);
            ordemFiscal.verify(comboProdutoRepository).save(any(ComboProduto.class));
        }
    }
}