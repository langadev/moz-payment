package com.langa.dev.payment_service.messaging.event;

import com.langa.dev.payment_service.domain.model.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentEvent(
        UUID paymentId,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        String phoneNumber,
        PaymentStatus status,
        String providerRef,
        String failureReason,
        Instant occurredAt
) {
    public static PaymentEvent of(UUID paymentId, String idempotencyKey, BigDecimal amount,
                                  String currency, String phoneNumber, PaymentStatus status) {
        return PaymentEvent.builder()
                .paymentId(paymentId)
                .idempotencyKey(idempotencyKey)
                .amount(amount)
                .currency(currency)
                .phoneNumber(phoneNumber)
                .status(status)
                .occurredAt(Instant.now())
                .build();
    }
}
