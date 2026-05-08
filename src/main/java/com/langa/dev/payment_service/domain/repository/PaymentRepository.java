package com.langa.dev.payment_service.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.langa.dev.payment_service.domain.model.Payment;
import com.langa.dev.payment_service.domain.model.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment,UUID> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderRef(String providerRef);

    List<Payment> findByStatusAndRetryCountLessThan(PaymentStatus status, int maxRetries);
    
}
