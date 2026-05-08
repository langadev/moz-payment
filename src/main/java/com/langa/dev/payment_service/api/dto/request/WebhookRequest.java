package com.langa.dev.payment_service.api.dto.request;

import com.langa.dev.payment_service.domain.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Notificação de status enviada pelo provedor externo")
public record WebhookRequest(

    @NotBlank
    @Schema(
        description = "Referência do pagamento no sistema do provedor.",
        example = "MOCK-A1B2C3D4"
    )
    String providerRef,

    @NotNull
    @Schema(
        description = "Novo status do pagamento. Apenas COMPLETED e FAILED são processados.",
        example = "COMPLETED",
        allowableValues = {"COMPLETED", "FAILED"}
    )
    PaymentStatus status,

    @Schema(
        description = "Motivo da falha. Obrigatório quando status=FAILED.",
        example = "Saldo insuficiente",
        nullable = true
    )
    String failureReason

) {}
