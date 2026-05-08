package com.langa.dev.payment_service.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:https://moz-payment.onrender.com}")
    private String baseUrl;

    @Bean
    OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Service API")
                        .description("""
                                Sistema de pagamentos resiliente para Moçambique.

                                ## Funcionalidades
                                - Criação de pagamentos com **idempotência** garantida
                                - Processamento assíncrono via RabbitMQ
                                - Retry automático com backoff exponencial (3 tentativas)
                                - Webhook para confirmação do provedor externo

                                ## Ciclo de vida do pagamento
                                ```
                                PENDING → PROCESSING → COMPLETED
                                                    └→ FAILED
                                ```

                                ## Idempotência
                                O campo `idempotencyKey` deve ser um UUID v4 gerado pelo cliente.
                                Reenviar o mesmo pedido com a mesma chave retorna o pagamento original (`200 OK`)
                                sem criar duplicados — seguro em redes instáveis.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Langa Dev")
                                .email("alfredolanga360@gmail.com"))
                        .license(new License()
                                .name("MIT")))
                .servers(List.of(
                        new Server().url(baseUrl).description("Servidor atual"),
                        new Server().url("https://moz-payment.onrender.com").description("Local")
                ))
                .tags(List.of(
                        new Tag().name("Payments").description("Criação e consulta de pagamentos"),
                        new Tag().name("Webhooks").description("Callbacks do provedor de pagamento externo")
                ));
    }
}
