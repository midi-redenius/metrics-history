package com.redenius.metrics.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI metricsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Metrics History API")
                        .version("0.1.0")
                        .description("Backend service for ingesting, storing, and querying metric time-series data")
                        .contact(new Contact().name("Andrej Redenius")));
    }
}
