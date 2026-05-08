package com.langa.dev.payment_service.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Dados para criação de um pagamento")
public class PaymentRequest {

    @NotBlank
    @Schema(
        description = "Chave única gerada pelo cliente para garantir idempotência. Use UUID v4.",
        example = "550e8400-e29b-41d4-a716-446655440000"
    )
    private String idempotencyKey;

    @NotNull
    @DecimalMin("0.01")
    @Schema(
        description = "Valor do pagamento. Mínimo: 0.01.",
        example = "150.00"
    )
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    @Schema(
        description = "Código ISO 4217 da moeda (3 letras).",
        example = "MZN",
        minLength = 3,
        maxLength = 3
    )
    private String currency;

    @NotBlank
    @Schema(
        description = "Número de telefone do pagador no formato internacional.",
        example = "+258840000000"
    )
    private String phoneNumber;

    @NotBlank
    @Schema(
        description = "Descrição curta do pagamento.",
        example = "Pagamento de serviço de água"
    )
    private String description;
}
