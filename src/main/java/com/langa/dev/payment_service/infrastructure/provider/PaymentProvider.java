package com.langa.dev.payment_service.infrastructure.provider;

import com.langa.dev.payment_service.domain.model.Payment;

public interface PaymentProvider {

    ProviderResponse initiatePayment(Payment payment);
}
