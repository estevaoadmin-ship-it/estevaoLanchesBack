package com.paullomaggio.estevaoLanches.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI estevaoLanchesOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .info(new Info()
                        .title("Estevão Lanches API")
                        .description("""
                                API completa do sistema Estevão Lanches.

                                Recursos:

                                • PDV
                                • Delivery
                                • Cardápio
                                • Clientes
                                • Pedidos
                                • Caixa
                                • Impressão
                                • Comandas
                                • Relatórios
                                • Autenticação JWT
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Paullo Maggio")
                                .email("contato@teste.com"))
                        .license(new License()
                                .name("MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentação do Projeto"));
    }

}