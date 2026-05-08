package com.langa.dev.payment_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langa.dev.payment_service.api.dto.request.PaymentRequest;
import com.langa.dev.payment_service.api.dto.request.WebhookRequest;
import com.langa.dev.payment_service.domain.model.Payment;
import com.langa.dev.payment_service.domain.model.PaymentStatus;
import com.langa.dev.payment_service.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("payment_db")
            .withUsername("payment_user")
            .withPassword("payment_secret");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PaymentRepository paymentRepository;

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    // ── POST /api/v1/payments ─────────────────────────────────────────────────

    @Test
    void createPayment_validRequest_returns201() throws Exception {
        PaymentRequest request = buildRequest("idem-001");

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.currency").value("MZN"));
    }

    @Test
    void createPayment_duplicateIdempotencyKey_returns200WithSamePayment() throws Exception {
        PaymentRequest request = buildRequest("idem-dup");
        String body = objectMapper.writeValueAsString(request);

        String firstResponse = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(firstResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void createPayment_missingRequiredField_returns422() throws Exception {
        String invalidBody = """
                {
                  "amount": 100.00,
                  "currency": "MZN",
                  "phoneNumber": "+258840000000",
                  "description": "Test"
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /api/v1/payments/{id} ─────────────────────────────────────────────

    @Test
    void getPayment_existingId_returns200() throws Exception {
        PaymentRequest request = buildRequest("idem-002");
        String created = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.idempotencyKey").value("idem-002"));
    }

    @Test
    void getPayment_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── POST /webhooks/provider ───────────────────────────────────────────────

    @Test
    void webhook_completed_updatesPaymentStatus() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .idempotencyKey("idem-webhook-1")
                .amount(new BigDecimal("200.00"))
                .currency("MZN")
                .phoneNumber("+258840000001")
                .description("Webhook test")
                .status(PaymentStatus.PROCESSING)
                .providerRef("PAY-WEBHOOK-1")
                .providerName("mock")
                .retryCount(0)
                .build());

        WebhookRequest webhook = new WebhookRequest("PAY-WEBHOOK-1", PaymentStatus.COMPLETED, null);

        mockMvc.perform(post("/webhooks/provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhook)))
                .andExpect(status().isOk());

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void webhook_unknownProviderRef_returns404() throws Exception {
        WebhookRequest webhook = new WebhookRequest("PAY-UNKNOWN", PaymentStatus.COMPLETED, null);

        mockMvc.perform(post("/webhooks/provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhook)))
                .andExpect(status().isNotFound());
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private PaymentRequest buildRequest(String idempotencyKey) {
        PaymentRequest r = new PaymentRequest();
        r.setIdempotencyKey(idempotencyKey);
        r.setAmount(new BigDecimal("150.00"));
        r.setCurrency("MZN");
        r.setPhoneNumber("+258840000000");
        r.setDescription("Integration test payment");
        return r;
    }
}
