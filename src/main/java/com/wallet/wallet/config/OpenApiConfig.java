package com.wallet.wallet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Wallet Service API")
                        .description("Mini electronic wallet service: deposit, withdraw, transfer with concurrency safety.")
                        .version("1.0.0")
                        .contact(new Contact().name("Your Name").email("you@example.com")))
                // Tell Swagger UI to display an "Authorize" button that asks for a JWT
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    // Force the section order in Swagger UI: Authentication first.
    // (application.yml must not override this with tags-sorter: alpha.)
    @Bean
    public OpenApiCustomizer tagOrderCustomizer() {
        return openApi -> openApi.setTags(List.of(
                new Tag().name("Authentication")
                        .description("Login and JWT token issuance. Demo users: alice / password (USER), admin / admin (ADMIN). "
                                + "Execute login, copy the token, then click Authorize and paste it."),
                new Tag().name("Accounts")
                        .description("Deposit, withdraw, balance and transaction history"),
                new Tag().name("Transfers")
                        .description("Transfer money between accounts")
        ));
    }
}