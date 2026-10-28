package com.narturebelle.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "firebaseBearerAuth";

    @Bean
    public OpenAPI narturebelleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NartureBelle API")
                        .description("Maternity-care platform backend. Most endpoints are public (product/content " +
                                "browsing, lead-gen forms). Auth is Firebase-only: call POST /api/v1/auth/firebase " +
                                "with a Bearer Firebase ID token first, then use the same token on /api/v1/users/** " +
                                "and anything else that requires login. See API_ENDPOINTS.md for the full access-level matrix.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a Firebase ID token here (no \"Bearer \" prefix needed, Swagger adds it).")));
    }
}
