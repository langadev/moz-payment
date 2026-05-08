package com.langa.dev.payment_service.infrastructure.provider;

import com.langa.dev.payment_service.domain.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResponse initiatePayment(Payment payment) {
        log.info("MockPaymentProvider: processing payment id={} amount={} {}",
                payment.getId(), payment.getAmount(), payment.getCurrency());
        String reference = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new ProviderResponse(reference);
    }
}
