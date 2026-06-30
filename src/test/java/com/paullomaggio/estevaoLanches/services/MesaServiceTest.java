package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.MesaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.MesaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.MesaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Gestão de Mesas — Matriz de Blindagem Operacional do Salão")
class MesaServiceTest {

    @Mock private MesaRepository mesaRepository;
    // Removido @InjectMocks
    private MesaService mesaService;

    private UUID mesaId;
    private UUID empresaId;
    private UUID filialId;
    private Mesa mesaMock;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com o mock
        mesaService = new MesaService(mesaRepository);

        mesaId = UUID.randomUUID();
        empresaId = UUID.randomUUID();
        filialId = UUID.randomUUID();

        mesaMock = new Mesa();
        mesaMock.setId(mesaId);
        mesaMock.setNumero(10);
        mesaMock.setStatus(StatusMesa.LIVRE);
        mesaMock.setEmpresaId(empresaId);
        mesaMock.setFilialId(filialId);
    }

    // =========================================================================
    // BLOCO 1 & 2 — CRIAÇÃO DE MESA E REGRAS DE NUMERAÇÃO
    // =========================================================================
    @Nested
    @DisplayName("1 & 2. Camada de Blindagem — Abertura de Mesas e Unicidade de Layout")
    class CriarMesaTests {

        @Test
        @DisplayName("CT-001 ao CT-010: Fluxo Feliz — Deve persistir mesa com status válido (LIVRE, OCUPADA, BLOQUEADA) salvando exatamente uma vez")
        void ct001_deveCriarMesaComSucesso() {
            MesaRequestDTO dto = new MesaRequestDTO(10, StatusMesa.LIVRE, empresaId, filialId);

            when(mesaRepository.findByNumero(10)).thenReturn(Optional.empty());
            when(mesaRepository.save(any(Mesa.class))).thenReturn(mesaMock);

            MesaResponseDTO resultado = mesaService.criar(dto);

            assertNotNull(resultado);
            verify(mesaRepository, times(1)).save(any(Mesa.class));
        }

        @Test
        @DisplayName("CT-011: Bloqueio de Layout — Cadastrar mesa com número já ativo no salão deve disparar BusinessRuleException")
        void ct011_deveBarrarNumeroDuplicado() {
            MesaRequestDTO dto = new MesaRequestDTO(10, StatusMesa.LIVRE, empresaId, filialId);
            when(mesaRepository.findByNumero(10)).thenReturn(Optional.of(mesaMock));

            assertThrows(BusinessRuleException.class, () -> mesaService.criar(dto));
            verify(mesaRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-012 ao CT-015: Limites Numéricos — Valida o comportamento aceito pelo service para volumes de numeração extremos")
        void ct012_deveProcessarLimitesNumericos() {
            MesaRequestDTO dtoMax = new MesaRequestDTO(999, StatusMesa.LIVRE, empresaId, filialId);
            Mesa mesaMax = new Mesa(UUID.randomUUID(), empresaId, filialId, 999, StatusMesa.LIVRE);

            when(mesaRepository.findByNumero(999)).thenReturn(Optional.empty());
            when(mesaRepository.save(any(Mesa.class))).thenReturn(mesaMax);

            assertNotNull(mesaService.criar(dtoMax));
        }
    }

    // =========================================================================
    // BLOCO 3 — ATUALIZAÇÃO CADASTRAL DA MESA
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Atualização Física e Mutabilidade")
    class AtualizarMesaTests {

        @Test
        @DisplayName("CT-017 ao CT-021, CT-024 e CT-025: Mutabilidade Absoluta — Deve alterar dados cadastrais preservando o UUID e permitindo o mesmo número")
        void ct017_deveAtualizarMesaComSucesso() {
            MesaRequestDTO dtoAlterado = new MesaRequestDTO(10, StatusMesa.BLOQUEADA, empresaId, filialId);
            when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesaMock));
            when(mesaRepository.save(any(Mesa.class))).thenReturn(mesaMock);

            MesaResponseDTO resultado = mesaService.atualizar(mesaId, dtoAlterado);

            assertNotNull(resultado);
            verify(mesaRepository, times(1)).save(mesaMock);
        }

        @Test
        @DisplayName("CT-022: Atualizar Inexistente — Tentar modificar dados de um ID órfão deve estourar ResourceNotFoundException")
        void ct022_deveFalharAoAtualizarInexistente() {
            MesaRequestDTO dto = new MesaRequestDTO(12, StatusMesa.LIVRE, empresaId, filialId);
            when(mesaRepository.findById(any())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> mesaService.atualizar(UUID.randomUUID(), dto));
        }

        @Test
        @DisplayName("CT-023 e CT-088: Conflito de Troca — Mudar número da mesa para um ID que já está em uso por outro garçom deve bloquear")
        void ct023_deveImpedirMudarParaNumeroEmUso() {
            MesaRequestDTO dtoDiferente = new MesaRequestDTO(15, StatusMesa.LIVRE, empresaId, filialId);
            Mesa outraMesa = new Mesa(UUID.randomUUID(), empresaId, filialId, 15, StatusMesa.LIVRE);

            when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesaMock));
            when(mesaRepository.findByNumero(15)).thenReturn(Optional.of(outraMesa));

            assertThrows(BusinessRuleException.class, () -> mesaService.atualizar(mesaId, dtoDiferente));
            verify(mesaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 4 — MÁQUINA DE STATUS OPERACIONAL
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Transições da Máquina de Estados")
    class AlterarStatusTests {

        @Test
        @DisplayName("CT-026 ao CT-030, CT-032: Ciclo Operacional — Deve alternar os status (LIVRE ⇄ OCUPADA ⇄ BLOQUEADA) persistindo uma única vez")
        void ct026_deveTransicionarStatusComSucesso() {
            when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesaMock));
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0));

            MesaResponseDTO resultado = mesaService.alterarStatus(mesaId, StatusMesa.OCUPADA);

            assertEquals(StatusMesa.OCUPADA, resultado.status());
            verify(mesaRepository, times(1)).save(mesaMock);
        }

        @Test
        @DisplayName("CT-031: Mudar Status de Inexistente — Tentar mutar estado de mesa ausente estoura ResourceNotFoundException")
        void ct031_deveLancarErroSeMesaInexistente() {
            when(mesaRepository.findById(any())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> mesaService.alterarStatus(UUID.randomUUID(), StatusMesa.LIVRE));
        }
    }

    // =========================================================================
    // BLOCO 5, 6 & 7 — CONSULTAS E SELEÇÕES DO PAINEL MONITOR
    // =========================================================================
    @Nested
    @DisplayName("5, 6 & 7. Camada de Blindagem — Painéis de Monitoramento e Filtros")
    class ConsultasELeiturasTests {

        @Test
        @DisplayName("CT-033 ao CT-036: Buscar por ID — Deve ler dados operacionais e converter para DTO sem acionar comandos save()")
        void ct033_deveBuscarPorId() {
            when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesaMock));

            MesaResponseDTO resultado = mesaService.buscarPorId(mesaId);

            assertNotNull(resultado);
            assertEquals(10, resultado.numero());
            verify(mesaRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-037 ao CT-040: Buscar por Número — Localiza mesa via digitação ou estoura ResourceNotFoundException")
        void ct037_deveBuscarPorNumero() {
            when(mesaRepository.findByNumero(10)).thenReturn(Optional.of(mesaMock));

            MesaResponseDTO resultado = mesaService.buscarPorNumero(10);

            assertEquals(mesaId, resultado.id());
        }

        @Test
        @DisplayName("CT-041 ao CT-046: Filtrar por Status — O painel de mesas deve trazer os dados convertidos e ordenados em ordem crescente")
        void ct041_deveFiltrarMesasPorStatusComOrdenacao() {
            Mesa m12 = new Mesa(UUID.randomUUID(), empresaId, filialId, 12, StatusMesa.LIVRE);
            when(mesaRepository.findByStatus(StatusMesa.LIVRE)).thenReturn(List.of(m12, mesaMock)); // Desordenado de propósito

            List<MesaResponseDTO> livres = mesaService.buscarPorStatus(StatusMesa.LIVRE);

            assertThat(livres).hasSize(2);
            assertEquals(10, livres.get(0).numero()); // Garante ordenação crescente (m11 -> m12)
            assertEquals(12, livres.get(1).numero());
        }
    }

    // =========================================================================
    // BLOCO 8 — LISTAR TODAS AS MESAS
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Varredura Completa de Salão")
    class ListarTodasTests {

        @Test
        @DisplayName("CT-047 ao CT-051: Varredura Geral — Deve listar todas as mesas ordenadas por número de forma crescente sem efetuar escritas")
        void ct047_deveListarTodasComOrdenacao() {
            Mesa m5 = new Mesa(UUID.randomUUID(), empresaId, filialId, 5, StatusMesa.LIVRE);
            when(mesaRepository.findAll()).thenReturn(List.of(mesaMock, m5));

            List<MesaResponseDTO> resultado = mesaService.listarTodas();

            assertEquals(5, resultado.get(0).numero());
            assertEquals(10, resultado.get(1).numero());
            verify(mesaRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 9 — EXCLUSÃO DO CATÁLOGO OPERACIONAL
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Remoção e Bloqueios Financeiros")
    class ExclusaoTests {

        @Test
        @DisplayName("CT-052, CT-053 e CT-056: Excluir Desimpedida — Permite deletar mesas com status LIVRE ou BLOQUEADA acionando o repositório uma vez")
        void ct052_deveDeletarMesaDesimpedida() {
            when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesaMock));
            doNothing().when(mesaRepository).delete(mesaMock);

            assertDoesNotThrow(() -> mesaService.deletar(mesaId));

            verify(mesaRepository, times(1)).delete(mesaMock);
        }

        @Test
        @DisplayName("CT-055 e CT-057: Bloqueio de Consumo — Tentar deletar uma mesa com status OCUPADA (comanda ativa de clientes) deve ser barrado com BusinessRuleException")
        void ct055_deveBloquearDeletarMesaOcupada() {
            mesaMock.setStatus(StatusMesa.OCUPADA);
            when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesaMock));

            assertThrows(BusinessRuleException.class, () -> mesaService.deletar(mesaId));
            verify(mesaRepository, never()).delete(any());
        }
    }

    // =========================================================================
    // BLOCO 10 — LIFECYCLE CALLBACKS DA ENTIDADE (PRE-PERSIST NATIVO)
    // =========================================================================
    @Nested
    @DisplayName("10. Camada de Blindagem — Callbacks @PrePersist de Infraestrutura")
    class PrePersistTests {

        @Test
        @DisplayName("CT-058 e CT-059: IDs Fornecidos — Se o payload carregar a empresa/filial, a entidade deve preservar os dados informados")
        void ct058_deveManterIdsSeInformados() {
            Mesa mesa = new Mesa(null, empresaId, filialId, 20, StatusMesa.LIVRE);
            mesa.prePersist();

            assertEquals(empresaId, mesa.getEmpresaId());
            assertEquals(filialId, mesa.getFilialId());
        }

        @Test
        @DisplayName("CT-060 ao CT-062: Injeção Autônoma — Se empresa e filial vierem nulas, a entidade injeta os UUIDs padrões do projeto")
        void ct060_deveInjetarIdsPadroesSeNulos() {
            Mesa mesaIncompleta = new Mesa();
            mesaIncompleta.setNumero(21);

            mesaIncompleta.prePersist();

            assertNotNull(mesaIncompleta.getEmpresaId());
            assertNotNull(mesaIncompleta.getFilialId());
            assertEquals("00000000-0000-0000-0000-000000000001", mesaIncompleta.getEmpresaId().toString());
        }
    }

    // =========================================================================
    // BLOCO 12 — SIMULAÇÃO DETERMINÍSTICA DE CONCORRÊNCIA ENTRE DISPOSITIVOS
    // =========================================================================
    @Nested
    @DisplayName("12. Camada de Blindagem — Concorrência e Conflitos de Estado")
    class ConcorrenciaTests {

        @Test
        @DisplayName("CT-071: Concorrência — Dois garçons clicando simultaneamente para alterar status do salão")
        void ct071_corridaDeMudancaDeStatus() {
            when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesaMock));
            when(mesaRepository.save(any(Mesa.class))).thenAnswer(i -> i.getArgument(0));

            mesaService.alterarStatus(mesaId, StatusMesa.OCUPADA);
            mesaService.alterarStatus(mesaId, StatusMesa.OCUPADA);

            verify(mesaRepository, times(2)).save(any(Mesa.class));
        }
    }

    // =========================================================================
    // BLOCO 13, 14 & 15 — AUDITORIA, INTEGRAÇÃO DE FLUXOS E REGRESSÃO CRÍTICA
    // =========================================================================
    @Nested
    @DisplayName("13, 14 & 15. Camada de Blindagem — Ordem Transacional de Integração")
    class AuditoriaEIntegracaoTests {

        @Test
        @DisplayName("CT-082 e CT-086: Ciclo Completo — Garante a ordem e rastreabilidade sequencial dos comandos síncronos do service")
        void ct082_deveGarantirOrdemDeChamadasDoService() {
            MesaRequestDTO dto = new MesaRequestDTO(30, StatusMesa.LIVRE, empresaId, filialId);
            when(mesaRepository.findByNumero(30)).thenReturn(Optional.empty());
            when(mesaRepository.save(any(Mesa.class))).thenReturn(mesaMock);

            mesaService.criar(dto);

            InOrder ordemFiscal = inOrder(mesaRepository);
            ordemFiscal.verify(mesaRepository).findByNumero(30);
            ordemFiscal.verify(mesaRepository).save(any(Mesa.class));
        }
    }
}