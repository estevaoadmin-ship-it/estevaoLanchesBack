package com.paullomaggio.estevaoLanches.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "comilao@gmail.com", roles = {"CLIENTE"}) // 🎯 Usuário comum do App Delivery
    @DisplayName("🛡️ SEGURANÇA: Um perfil de CLIENTE deve ser terminantemente proibido de acessar relatórios gerenciais da retaguarda")
    void clienteNaoDeveAcessarRelatoriosAdmin() throws Exception {
        mockMvc.perform(get("/api/relatorios/faturamento")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // 🚨 Deve retornar 403 Forbidden com segurança
    }

    @Test
    @WithMockUser(username = "comilao@gmail.com", roles = {"CLIENTE"})
    @DisplayName("🛡️ SEGURANÇA: Um perfil de CLIENTE deve ser proibido de criar ou listar novos colaboradores internos")
    void clienteNaoDeveManipularUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Hacker\",\"email\":\"hacker@admin.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("🛡️ SEGURANÇA: Endpoints públicos de login de funcionários devem responder normalmente sem travas")
    void rotasDeAutenticacaoDevemSerPublicas() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"teste@teste.com\",\"senha\":\"123\"}"))
                .andExpect(status().isUnauthorized()); // 🚨 Retorna 401 (Credenciais Erradas) mas NÃO 403, provando que a rota é pública!
    }
}