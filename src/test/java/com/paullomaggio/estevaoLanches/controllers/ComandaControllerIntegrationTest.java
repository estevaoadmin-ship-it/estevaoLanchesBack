package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.entities.Subconta;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import com.paullomaggio.estevaoLanches.repositories.ComandaRepository;
import com.paullomaggio.estevaoLanches.repositories.MesaRepository;
import com.paullomaggio.estevaoLanches.repositories.SubcontaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // 🚀 INCLUÍDO
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"}) // 🚀 CORRIGIDO: Fornece o contexto JWT simulado para zerar o erro 403 Forbidden!
class ComandaControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MesaRepository mesaRepository;
    @Autowired private ComandaRepository comandaRepository;
    @Autowired private SubcontaRepository subcontaRepository;

    @Test
    @DisplayName("Teste 24: POST /api/comandas/abrir/5 - Deve responder HTTP 200 OK")
    void deveRetornarStatus200AoAbrirMesaValida() throws Exception {
        mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABERTA"));
    }

    @Test
    @DisplayName("Teste 25: Deve constar fisicamente a criação da Mesa 5 com status OCUPADA no banco H2")
    void deveVerificarMesaCriadaNoBanco() throws Exception {
        mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Optional<Mesa> mesaNoBanco = mesaRepository.findByNumero(5);
        assertThat(mesaNoBanco).isPresent();
        assertThat(mesaNoBanco.get().getStatus()).isEqualTo(StatusMesa.OCUPADA);
    }

    @Test
    @DisplayName("Teste 26: Deve gerar de forma íntegra o registro da comanda vinculada à mesa no banco")
    void deveVerificarComandaCriadaNoBanco() throws Exception {
        mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        List<Comanda> comandas = comandaRepository.findAll();
        assertThat(comandas).isNotEmpty();
        assertThat(comandas.get(0).getStatus()).isEqualTo(StatusComanda.ABERTA);
    }

    @Test
    @DisplayName("Teste 27: Deve constar na tabela a Subconta 1 associada à nova comanda gerada")
    void deveVerificarSubcontaCriadaNoBanco() throws Exception {
        mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        List<Subconta> subcontas = subcontaRepository.findAll();
        assertThat(subcontas).isNotEmpty();
        assertThat(subcontas.get(0).getNumeroConta()).isEqualTo(1);
        assertThat(subcontas.get(0).getPago()).isFalse();
    }

    @Test
    @DisplayName("Teste 28: Idempotência de Chamadas - Segunda requisição consecutiva deve retornar a comanda idêntica")
    void deveGarantirIdempotenciaRetornandoMesmoId() throws Exception {
        String response1 = mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String response2 = mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response1).isEqualTo(response2);
    }
}