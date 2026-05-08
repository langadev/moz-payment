package com.langa.dev.payment_service.api.dto.response;

import com.langa.dev.payment_service.domain.model.Payment;
import com.langa.dev.payment_service.domain.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Detalhes de um pagamento")
public record PaymentResponse(

    @Schema(description = "ID único do pagamento", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID id,

    @Schema(description = "Chave de idempotência fornecida pelo cliente", example = "550e8400-e29b-41d4-a716-446655440000")
    String idempotencyKey,

    @Schema(description = "Valor do pagamento", example = "150.00")
    BigDecimal amount,

    @Schema(description = "Código ISO 4217 da moeda", example = "MZN")
    String currency,

    @Schema(description = "Número de telefone do pagador", example = "+258840000000")
    String phoneNumber,

    @Schema(description = "Descrição do pagamento", example = "Pagamento de serviço de água")
    String description,

    @Schema(description = "Estado actual do pagamento", example = "PENDING")
    PaymentStatus status,

    @Schema(description = "Referência do pagamento no sistema do provedor", example = "MOCK-A1B2C3D4", nullable = true)
    String providerRef,

    @Schema(description = "Nome do provedor que processou o pagamento", example = "mock", nullable = true)
    String providerName,

    @Schema(description = "Motivo da falha, se aplicável", example = "Saldo insuficiente", nullable = true)
    String failureReason,

    @Schema(description = "Número de tentativas de processamento", example = "0")
    int retryCount,

    @Schema(description = "Data e hora de criação", example = "2026-05-08T10:30:00")
    LocalDateTime createdAt,

    @Schema(description = "Data e hora da última actualização", example = "2026-05-08T10:30:05")
    LocalDateTime updatedAt

) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getIdempotencyKey(),
                p.getAmount(),
                p.getCurrency(),
                p.getPhoneNumber(),
                p.getDescription(),
                p.getStatus(),
                p.getProviderRef(),
                p.getProviderName(),
                p.getFailureReason(),
                p.getRetryCount(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
