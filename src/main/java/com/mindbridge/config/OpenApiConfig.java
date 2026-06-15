package com.mindbridge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mindBridgeOpenAPI() {
        // Define the Bearer token security scheme
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .name("Authorization")
                .description("Paste your JWT token here. Get it from POST /api/auth/login");

        // Apply it globally to all endpoints
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("Bearer Authentication");

        return new OpenAPI()
                .info(new Info()
                        .title("MindBridge API")
                        .description("""
                    **MindBridge** — AI-Powered Burnout Prevention Platform

                    This API powers:
                    - 🤖 **Check-in Agent** — Daily AI wellness conversations with employees
                    - 📊 **Pattern Agent** — Burnout risk scoring over 14-day history
                    - 🖥️ **HR Dashboard** — Team wellness overview and alerts

                    **How to authenticate:**
                    1. Call `POST /api/auth/register` or `POST /api/auth/login`
                    2. Copy the `token` from the response
                    3. Click the **Authorize 🔒** button above and paste the token
                    """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MindBridge Team")
                                .email("hello@mindbridge.ai"))
                        .license(new License()
                                .name("Private")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")
                ))
                // ── This adds the 🔒 Authorize button to Swagger UI ──
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", bearerScheme))
                .addSecurityItem(securityRequirement);
    }
}