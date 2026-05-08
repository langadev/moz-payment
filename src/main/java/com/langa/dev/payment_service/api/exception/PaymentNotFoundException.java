package com.langa.dev.payment_service.api.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("Payment not found: " + id);
    }

    public PaymentNotFoundException(String field, String value) {
        super("Payment not found: " + field + "=" + value);
    }
}
