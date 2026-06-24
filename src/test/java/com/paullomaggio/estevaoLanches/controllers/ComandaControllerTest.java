package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.services.ComandaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Testes de Controller — ComandaController")
class ComandaControllerTest {

    @Mock
    private ComandaService comandaService;

    @InjectMocks
    private ComandaController comandaController;

    private ComandaResponseDTO comandaResponseMock;
    private UUID comandaId;

    @BeforeEach
    void setUp() {
        comandaId = UUID.randomUUID();

        // 🎯 FIX NOVA LÓGICA: O Controller agora trafega estritamente Records/DTOs imutáveis
        comandaResponseMock = new ComandaResponseDTO(
                comandaId,
                StatusComanda.ABERTA,
                LocalDateTime.now(),
                null,
                UUID.randomUUID(), // empresaId
                UUID.randomUUID(), // filialId
                8,                 // numeroMesa
                false              // idJaExistia
        );
    }

    @Test
    @DisplayName("Deve retornar status 200/201 e o DTO correspondente ao abrir comanda com sucesso")
    void deveAbrirComandaComSucesso() {
        // Alinhado com o método focado por número de mesa do ComandaService
        when(comandaService.abrirPorNumeroMesa(8)).thenReturn(comandaResponseMock);

        ResponseEntity<ComandaResponseDTO> response = comandaController.abrirComanda(8);

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(comandaId);
        assertThat(response.getBody().status()).isEqualTo(StatusComanda.ABERTA);
        assertThat(response.getBody().numeroMesa()).isEqualTo(8);

        verify(comandaService, times(1)).abrirPorNumeroMesa(8);
    }

    @Test
    @DisplayName("Deve delegar o fechamento e retornar o contrato DTO da comanda encerrada com sucesso")
    void deveFecharComandaComSucesso() {
        // Instancia o DTO de resposta simulando o estado final de fechamento no caixa
        ComandaResponseDTO comandaFechadaMock = new ComandaResponseDTO(
                comandaId,
                StatusComanda.FECHADA,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                comandaResponseMock.empresaId(),
                comandaResponseMock.filialId(),
                8,
                false
        );

        when(comandaService.fecharComanda(comandaId)).thenReturn(comandaFechadaMock);

        ResponseEntity<ComandaResponseDTO> response = comandaController.fecharComanda(comandaId);

        // Retorna HTTP 200 OK contendo os metadados de fechamento para sincronismo síncreno do front-end
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(StatusComanda.FECHADA);
        assertThat(response.getBody().fechadaEm()).isNotNull();

        verify(comandaService, times(1)).fecharComanda(comandaId);
    }
}