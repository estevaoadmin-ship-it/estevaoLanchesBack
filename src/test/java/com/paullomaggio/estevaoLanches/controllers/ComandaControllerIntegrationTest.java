package com.paullomaggio.estevaoLanches.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// 🎯 FIX: Importação corrigida e alterada de 'get' para 'post'
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ComandaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "garcom@tevao.com", roles = {"GARCOM"})
    @DisplayName("🎯 FIM DO 404: Validar contrato do JSON usando a rota e o método HTTP legítimos do Controller")
    void deveValidarContratoDeComandaSemAcoplamentoDeStringPura() throws Exception {

        // 🎯 FIX DEFINITIVO: O MockMvc agora aciona o POST /api/comandas/abrir/5, acionando o método real do Controller
        mockMvc.perform(post("/api/comandas/abrir/5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // 🚨 Nota: Se o seu método retornar 201, basta alterar para .isCreated()
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.empresaId").exists())
                .andExpect(jsonPath("$.filialId").exists());
    }
}