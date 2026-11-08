package com.example.claims.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document at /v3/api-docs, Swagger UI at /swagger-ui.html. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI claimsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Claims Service API")
                        .version("v1")
                        .description("""
                                Healthcare claims intake and adjudication.

                                * Authenticate with `POST /api/v1/auth/token`, then send `Authorization: Bearer <token>`.
                                * Roles: **SUBMITTER** (submit/view) and **ADJUDICATOR** (also adjudicate/change status).
                                * Every error is RFC 7807 `application/problem+json` and includes a `correlationId`.
                                * Send `X-Correlation-Id` to trace a request across services; it is echoed back."""))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
