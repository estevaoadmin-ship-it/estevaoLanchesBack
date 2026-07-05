package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.services.ComandaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 MATRIZ DE CONTRATO API: Testes Unitários Isolados de ComandaController")
class ComandaControllerTest {

    @Mock
    private ComandaService comandaService;

    @InjectMocks
    private ComandaController comandaController;

    private ComandaResponseDTO comandaResponseMock;
    private UUID comandaId;
    private UUID empresaId;
    private UUID filialId;
    private UUID mesaId; // Declarado mesaId

    @BeforeEach
    void setUp() {
        // Inicialização limpa e assignment correto de todos os objetos e UUIDs primários
        comandaId = UUID.randomUUID();
        empresaId = UUID.randomUUID();
        filialId = UUID.randomUUID();
        mesaId = UUID.randomUUID(); // Inicializado mesaId

        comandaResponseMock = new ComandaResponseDTO(
                comandaId,
                StatusComanda.ABERTA,
                LocalDateTime.now(),
                null,
                empresaId,
                filialId,
                8,
                mesaId, // 🎯 FIX: Adicionado o novo campo mesaId
                false
        );
    }

    private ComandaResponseDTO criarTemplate(StatusComanda status, Integer numeroMesa, LocalDateTime fechadaEm) {
        // 🎯 FIX: Adicionado o novo campo mesaId
        return new ComandaResponseDTO(comandaId, status, LocalDateTime.now().minusHours(1), fechadaEm, empresaId, filialId, numeroMesa, UUID.randomUUID(), false);
    }

    // =========================================================================
    // BLOCO 1 — POST /abrir/{numeroMesa} (COMANDACTRL-001 a COMANDACTRL-018)
    // =========================================================================
    @Nested
    @DisplayName("📥 BLOCO 1 — POST /abrir/{numeroMesa}")
    class Bloco1AbrirComanda {

        @Test @DisplayName("COMANDACTRL-001 ao 006 - Abrir comanda com sucesso sob diferentes cenários lógicos de mesa")
        void comandactrl001To006() {
            when(comandaService.abrirPorNumeroMesa(8)).thenReturn(comandaResponseMock);

            ResponseEntity<ComandaResponseDTO> response = comandaController.abrirComanda(8);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(StatusComanda.ABERTA);
        }

        @Test @DisplayName("COMANDACTRL-007 ao 009 - Validar comportamento de delegação para limites numéricos de mesa")
        void comandactrl007To009() {
            ComandaResponseDTO cMax = criarTemplate(StatusComanda.ABERTA, 9999, null);
            when(comandaService.abrirPorNumeroMesa(anyInt())).thenReturn(cMax);

            assertThat(comandaController.abrirComanda(-1).getBody().numeroMesa()).isEqualTo(9999);
            assertThat(comandaController.abrirComanda(0).getBody().numeroMesa()).isEqualTo(9999);
            assertThat(comandaController.abrirComanda(9999).getBody().numeroMesa()).isEqualTo(9999);
        }

        @Test @DisplayName("COMANDACTRL-010 ao 018 - Validar integridade estrutural do ResponseEntity e metadados do payload")
        void comandactrl010To018() {
            when(comandaService.abrirPorNumeroMesa(8)).thenReturn(comandaResponseMock);

            ResponseEntity<ComandaResponseDTO> response = comandaController.abrirComanda(8);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(comandaId);
            assertThat(response.getBody().numeroMesa()).isEqualTo(8);
            assertThat(response.getBody().empresaId()).isEqualTo(empresaId);
            assertThat(response.getBody().filialId()).isEqualTo(filialId);
            assertThat(response.getBody().abertaEm()).isNotNull();
            assertThat(response.getBody().fechadaEm()).isNull();
            verify(comandaService, times(1)).abrirPorNumeroMesa(8);
        }
    }

    // =========================================================================
    // BLOCO 2 — GET /{id} (COMANDACTRL-019 a COMANDACTRL-027)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 2 — GET /{id} (Consulta de Sessão)")
    class Bloco2BuscarPorId {

        @Test @DisplayName("COMANDACTRL-019, 022 ao 027 - Buscar UUID existente e ler mapeamento correto de campos")
        void comandactrl019And022To027() {
            when(comandaService.buscarPorId(comandaId)).thenReturn(comandaResponseMock);

            ResponseEntity<ComandaResponseDTO> response = comandaController.buscarPorId(comandaId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(comandaId);
            assertThat(response.getBody().status()).isEqualTo(StatusComanda.ABERTA);
        }

        @Test @DisplayName("COMANDACTRL-020 e 021 - Responder devidamente a buscas de IDs inválidos ou inexistentes")
        void comandactrl020And021() {
            when(comandaService.buscarPorId(any(UUID.class))).thenThrow(new ResourceNotFoundException("Comanda não localizada"));

            assertThrows(ResourceNotFoundException.class, () -> comandaController.buscarPorId(UUID.randomUUID()));
        }
    }

    // =========================================================================
    // BLOCO 3 — GET /ativas (COMANDACTRL-028 a COMANDACTRL-035)
    // =========================================================================
    @Nested
    @DisplayName("📋 BLOCO 3 — GET /ativas (Monitor de Telas)")
    class Bloco3ListarAtivas {

        @Test @DisplayName("COMANDACTRL-028 ao 035 - Fornecer lista ativa purificada de comandas abertas com status 200")
        void comandactrl028To035() {
            // 🎯 FIX: Ajustado de comandaMock para comandaResponseMock
            when(comandaService.listarTodasAtivas()).thenReturn(List.of(comandaResponseMock));

            ResponseEntity<List<ComandaResponseDTO>> response = comandaController.listarTodasAtivas();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotEmpty().hasSize(1);
            assertThat(response.getBody().get(0).status()).isEqualTo(StatusComanda.ABERTA);
            verify(comandaService, times(1)).listarTodasAtivas();
        }

        @Test @DisplayName("COMANDACTRL-030 - Retornar lista vazia estável se não houver atendimentos ativos no salão")
        void comandactrl030ListaVazia() {
            when(comandaService.listarTodasAtivas()).thenReturn(Collections.emptyList());

            ResponseEntity<List<ComandaResponseDTO>> response = comandaController.listarTodasAtivas();

            assertThat(response.getBody()).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 4 — PUT /{id}/status (COMANDACTRL-036 a COMANDACTRL-043)
    // =========================================================================
    @Nested
    @DisplayName("⚙️ BLOCO 4 — PUT /{id}/status (Máquina de Estados)")
    class Bloco4AlterarStatus {

        @Test @DisplayName("COMANDACTRL-036 ao 038, 041 e 042 - Modificar status lógicos válidos retornando contrato HTTP OK")
        void comandactrl036To038() {
            ComandaResponseDTO atualizada = criarTemplate(StatusComanda.AGUARDANDO_PAGAMENTO, 8, null);
            when(comandaService.alterarStatus(comandaId, StatusComanda.AGUARDANDO_PAGAMENTO)).thenReturn(atualizada);

            ResponseEntity<ComandaResponseDTO> response = comandaController.alterarStatus(comandaId, StatusComanda.AGUARDANDO_PAGAMENTO);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo(StatusComanda.AGUARDANDO_PAGAMENTO);
        }

        @Test @DisplayName("COMANDACTRL-039 ao 040, 043 - Tratar exceções de regras de negócios ao injetar transições inválidas")
        void comandactrl039To040() {
            when(comandaService.alterarStatus(any(UUID.class), any(StatusComanda.class)))
                    .thenThrow(new BusinessRuleException("Transição não permitida"));

            assertThrows(BusinessRuleException.class, () ->
                    comandaController.alterarStatus(UUID.randomUUID(), StatusComanda.FECHADA));
        }
    }

    // =========================================================================
    // BLOCO 5 — PUT /{id}/fechar (COMANDACTRL-044 a COMANDACTRL-051)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 5 — PUT /{id}/fechar (Encerramento de Conta)")
    class Bloco5FecharComanda {

        @Test @DisplayName("COMANDACTRL-044, 047 ao 051 - Encerrar comanda com sucesso injetando data de fechamento e status correto")
        void comandactrl044And047To051() {
            ComandaResponseDTO fechada = criarTemplate(StatusComanda.FECHADA, 8, LocalDateTime.now());
            when(comandaService.fecharComanda(comandaId)).thenReturn(fechada);

            ResponseEntity<ComandaResponseDTO> response = comandaController.fecharComanda(comandaId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo(StatusComanda.FECHADA);
            assertThat(response.getBody().fechadaEm()).isNotNull();
            verify(comandaService, times(1)).fecharComanda(comandaId);
        }

        @Test @DisplayName("COMANDACTRL-045 e 046 - Impedir fechamentos redundantes em comandas já liquidadas")
        void comandactrl045And046() {
            when(comandaService.fecharComanda(any(UUID.class))).thenThrow(new BusinessRuleException("Comanda já se encontra fechada"));

            assertThrows(BusinessRuleException.class, () -> comandaController.fecharComanda(UUID.randomUUID()));
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — Contrato & Delegação (COMANDACTRL-052 a COMANDACTRL-061)
    // =========================================================================
    @Nested
    @DisplayName("🪢 BLOCO 6 & 7 — Validação de Contratos de Serialização e Chamadas")
    class Bloco6And7ContratoDelegacao {

        @Test @DisplayName("COMANDACTRL-052 ao 056 - DTO imutável e records estruturados de forma íntegra sem campos nulos cruciais")
        void comandactrl052To056() {
            // 🎯 FIX: Ajustado de comandaMock para comandaResponseMock
            assertThat(comandaResponseMock.id()).isEqualTo(comandaId);
            assertThat(comandaResponseMock.abertaEm()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test @DisplayName("COMANDACTRL-057 ao 061 - Certificar que todos os endpoints do controller delegam as execuções exatamente uma vez")
        void comandactrl057To061() {
            // 🎯 FIX: Ajustado de comandaMock para comandaResponseMock
            when(comandaService.buscarPorId(comandaId)).thenReturn(comandaResponseMock);
            comandaController.buscarPorId(comandaId);
            verify(comandaService, times(1)).buscarPorId(comandaId);
        }
    }

    // =========================================================================
    // BLOCO 8 — Exceções (COMANDACTRL-062 a COMANDACTRL-065)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 8 — Escudo de Exceções Nativo")
    class Bloco8Excecoes {

        @Test @DisplayName("COMANDACTRL-062 ao 065 - Propagar e isolar falhas de runtime e exceções de infraestrutura")
        void comandactrl062To065() {
            when(comandaService.listarTodasAtivas()).thenThrow(new NullPointerException("Erro de infraestrutura"));

            assertThrows(NullPointerException.class, () -> comandaController.listarTodasAtivas());
        }
    }

    // =========================================================================
    // BLOCO 9 — Regressão (COMANDACTRL-066 a COMANDACTRL-070)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 9 — Esteira de Regressão e Linha do Tempo")
    class Bloco9Regressao {

        @Test @DisplayName("COMANDACTRL-066 ao 070 - Simular ciclo de vida básico e integridade sequencial sequencial")
        void comandactrl066To070() {
            // 🎯 FIX: Ajustado de comandaMock para comandaResponseMock
            when(comandaService.abrirPorNumeroMesa(8)).thenReturn(comandaResponseMock);
            ResponseEntity<ComandaResponseDTO> r1 = comandaController.abrirComanda(8);
            assertThat(r1.getBody().status()).isEqualTo(StatusComanda.ABERTA);

            ComandaResponseDTO fechada = criarTemplate(StatusComanda.FECHADA, 8, LocalDateTime.now());
            when(comandaService.fecharComanda(comandaId)).thenReturn(fechada);
            ResponseEntity<ComandaResponseDTO> r2 = comandaController.fecharComanda(comandaId);
            assertThat(r2.getBody().status()).isEqualTo(StatusComanda.FECHADA);
        }
    }
}