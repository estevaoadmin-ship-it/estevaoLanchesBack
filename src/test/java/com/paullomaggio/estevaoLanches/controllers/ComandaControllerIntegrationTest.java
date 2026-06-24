package com.paullomaggio.estevaoLanches.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // 🎯 Isolamento: Garante o uso do banco de dados em memória (H2)
@Transactional // 🎯 Integridade: Executa o Rollback automático ao fim do teste
class ComandaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    @DisplayName("🎯 FIM DO 404: Validar contrato do JSON usando a rota e o método HTTP legítimos do Controller")
    void deveValidarContratoDeComandaSemAcoplamentoDeStringPura() throws Exception {

        // 🎯 FIX DEFINITIVO: O MockMvc agora aciona o POST /api/comandas/abrir/5, batendo no método real do Controller
        mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // 🚨 Valida HTTP 200 (Altere para isCreated() caso o Controller retorne HTTP 201)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.empresaId").exists())
                .andExpect(jsonPath("$.filialId").exists());
    }
}