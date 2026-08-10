package com.luxray.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI luxrayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LuxRay Auth Service")
                        .description("Endpoints de autenticación y registro de usuarios")
                        .version("v1"));
    }
}
