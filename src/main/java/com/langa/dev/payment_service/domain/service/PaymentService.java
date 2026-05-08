package com.langa.dev.payment_service.domain.service;

import com.langa.dev.payment_service.api.dto.request.PaymentRequest;
import com.langa.dev.payment_service.api.dto.request.WebhookRequest;
import com.langa.dev.payment_service.api.exception.PaymentNotFoundException;
import com.langa.dev.payment_service.domain.model.Payment;
import com.langa.dev.payment_service.domain.model.PaymentStatus;
import com.langa.dev.payment_service.domain.repository.PaymentRepository;
import com.langa.dev.payment_service.infrastructure.provider.PaymentProvider;
import com.langa.dev.payment_service.infrastructure.provider.ProviderResponse;
import com.langa.dev.payment_service.messaging.event.PaymentEvent;
import com.langa.dev.payment_service.messaging.publisher.PaymentEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentEventPublisher eventPublisher;
    private final Counter paymentsCreated;
    private final Counter paymentsFailed;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentProvider paymentProvider,
                          PaymentEventPublisher eventPublisher,
                          MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.eventPublisher = eventPublisher;
        this.paymentsCreated = Counter.builder("payments.created")
                .description("Total payments created")
                .register(meterRegistry);
        this.paymentsFailed = Counter.builder("payments.provider.failed")
                .description("Total provider call failures")
                .register(meterRegistry);
    }

    @Transactional
    public PaymentResult createPayment(PaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent request: returning existing payment idempotencyKey={}", request.getIdempotencyKey());
            return new PaymentResult(existing.get(), false);
        }

        Payment payment = Payment.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .phoneNumber(request.getPhoneNumber())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        MDC.put("paymentId", payment.getId().toString());
        log.info("Payment created: id={} amount={} {}", payment.getId(), payment.getAmount(), payment.getCurrency());

        // Publica no RabbitMQ para processamento assíncrono
        PaymentEvent event = PaymentEvent.of(
                payment.getId(), payment.getIdempotencyKey(),
                payment.getAmount(), payment.getCurrency(),
                payment.getPhoneNumber(), PaymentStatus.PENDING);
        eventPublisher.publishProcessing(event);

        return new PaymentResult(payment, true);
    }

    /**
     * Chamado pelo consumer da fila payment.processing.
     * O @Retryable funciona porque o consumer chama este método via proxy Spring.
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    public void processPayment(PaymentEvent event) {
        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(event.paymentId()));

        MDC.put("paymentId", payment.getId().toString());

        try {
            ProviderResponse response = paymentProvider.initiatePayment(payment);
            payment.setProviderRef(response.reference());
            payment.setProviderName("mock");
            payment.setStatus(PaymentStatus.PROCESSING);
            log.info("Provider accepted payment id={} ref={}", payment.getId(), response.reference());
        } catch (Exception e) {
            payment.setRetryCount(payment.getRetryCount() + 1);
            payment.setFailureReason(e.getMessage());
            log.warn("Provider call failed for payment id={}: {}", payment.getId(), e.getMessage());
            throw e;
        }

        paymentRepository.save(payment);
        paymentsCreated.increment();

        eventPublisher.publishCompleted(PaymentEvent.builder()
                .paymentId(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .phoneNumber(payment.getPhoneNumber())
                .status(PaymentStatus.PROCESSING)
                .providerRef(payment.getProviderRef())
                .occurredAt(Instant.now())
                .build());
    }

    @Recover
    @Transactional
    public void recoverProcessPayment(Exception e, PaymentEvent event) {
        paymentsFailed.increment();
        paymentRepository.findById(event.paymentId()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.PENDING);
            payment.setFailureReason("Provider unreachable after retries: " + e.getMessage());
            paymentRepository.save(payment);
            log.error("Provider permanently failed for payment id={}, left as PENDING for retry job", payment.getId());
        });

        eventPublisher.publishFailed(PaymentEvent.builder()
                .paymentId(event.paymentId())
                .idempotencyKey(event.idempotencyKey())
                .amount(event.amount())
                .currency(event.currency())
                .phoneNumber(event.phoneNumber())
                .status(PaymentStatus.FAILED)
                .failureReason("Provider unreachable after retries: " + e.getMessage())
                .occurredAt(Instant.now())
                .build());
    }

    @Transactional
    public void handleWebhook(WebhookRequest request) {
        Payment payment = paymentRepository.findByProviderRef(request.providerRef())
                .orElseThrow(() -> new PaymentNotFoundException("providerRef", request.providerRef()));

        MDC.put("paymentId", payment.getId().toString());

        PaymentStatus newStatus = request.status();
        if (newStatus != PaymentStatus.COMPLETED && newStatus != PaymentStatus.FAILED) {
            log.warn("Webhook ignored: unsupported status={} for providerRef={}", newStatus, request.providerRef());
            return;
        }

        payment.setStatus(newStatus);
        if (request.failureReason() != null) {
            payment.setFailureReason(request.failureReason());
        }
        paymentRepository.save(payment);
        log.info("Webhook processed: providerRef={} status={}", request.providerRef(), newStatus);

        PaymentEvent event = PaymentEvent.builder()
                .paymentId(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .phoneNumber(payment.getPhoneNumber())
                .status(newStatus)
                .providerRef(payment.getProviderRef())
                .failureReason(request.failureReason())
                .occurredAt(Instant.now())
                .build();

        if (newStatus == PaymentStatus.COMPLETED) {
            eventPublisher.publishCompleted(event);
        } else {
            eventPublisher.publishFailed(event);
        }
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public record PaymentResult(Payment payment, boolean created) {}
}
