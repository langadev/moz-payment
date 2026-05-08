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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentProvider paymentProvider;
    @Mock PaymentEventPublisher eventPublisher;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, paymentProvider, eventPublisher, new SimpleMeterRegistry());
    }

    // ── createPayment ─────────────────────────────────────────────────────────

    @Test
    void createPayment_newRequest_savesAndPublishes() {
        PaymentRequest request = buildRequest("key-001");
        Payment saved = buildPayment(request);

        when(paymentRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenReturn(saved);

        PaymentService.PaymentResult result = service.createPayment(request);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository).save(any(Payment.class));
        verify(eventPublisher).publishProcessing(any(PaymentEvent.class));
    }

    @Test
    void createPayment_duplicateIdempotencyKey_returnsExistingWithoutSaving() {
        PaymentRequest request = buildRequest("key-dup");
        Payment existing = buildPayment(request);

        when(paymentRepository.findByIdempotencyKey("key-dup")).thenReturn(Optional.of(existing));

        PaymentService.PaymentResult result = service.createPayment(request);

        assertThat(result.created()).isFalse();
        assertThat(result.payment()).isSameAs(existing);
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishProcessing(any());
    }

    // ── processPayment ────────────────────────────────────────────────────────

    @Test
    void processPayment_providerSuccess_setsProcessingAndPublishesCompleted() {
        Payment payment = buildPayment(buildRequest("key-002"));
        PaymentEvent event = buildEvent(payment);

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentProvider.initiatePayment(payment)).thenReturn(new ProviderResponse("REF-XYZ"));
        when(paymentRepository.save(any())).thenReturn(payment);

        service.processPayment(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getProviderRef()).isEqualTo("REF-XYZ");

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publishCompleted(captor.capture());
        assertThat(captor.getValue().providerRef()).isEqualTo("REF-XYZ");
    }

    @Test
    void processPayment_paymentNotFound_throwsException() {
        UUID id = UUID.randomUUID();
        PaymentEvent event = PaymentEvent.builder()
                .paymentId(id)
                .idempotencyKey("k")
                .amount(BigDecimal.ONE)
                .currency("MZN")
                .phoneNumber("+258840000000")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processPayment(event))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ── handleWebhook ─────────────────────────────────────────────────────────

    @Test
    void handleWebhook_completed_updatesStatusAndPublishesCompleted() {
        Payment payment = buildPayment(buildRequest("key-003"));
        payment.setProviderRef("REF-ABC");

        when(paymentRepository.findByProviderRef("REF-ABC")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        service.handleWebhook(new WebhookRequest("REF-ABC", PaymentStatus.COMPLETED, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(eventPublisher).publishCompleted(any(PaymentEvent.class));
        verify(eventPublisher, never()).publishFailed(any());
    }

    @Test
    void handleWebhook_failed_updatesStatusAndPublishesFailed() {
        Payment payment = buildPayment(buildRequest("key-004"));
        payment.setProviderRef("REF-DEF");

        when(paymentRepository.findByProviderRef("REF-DEF")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        service.handleWebhook(new WebhookRequest("REF-DEF", PaymentStatus.FAILED, "Insufficient funds"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Insufficient funds");
        verify(eventPublisher).publishFailed(any(PaymentEvent.class));
    }

    @Test
    void handleWebhook_unsupportedStatus_ignoredWithoutSaving() {
        Payment payment = buildPayment(buildRequest("key-005"));
        payment.setProviderRef("REF-GHI");

        when(paymentRepository.findByProviderRef("REF-GHI")).thenReturn(Optional.of(payment));

        service.handleWebhook(new WebhookRequest("REF-GHI", PaymentStatus.PENDING, null));

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleWebhook_unknownProviderRef_throwsNotFound() {
        when(paymentRepository.findByProviderRef("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleWebhook(new WebhookRequest("UNKNOWN", PaymentStatus.COMPLETED, null)))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ── getPayment ────────────────────────────────────────────────────────────

    @Test
    void getPayment_exists_returnsPayment() {
        Payment payment = buildPayment(buildRequest("key-006"));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        Payment result = service.getPayment(payment.getId());

        assertThat(result).isSameAs(payment);
    }

    @Test
    void getPayment_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PaymentRequest buildRequest(String idempotencyKey) {
        PaymentRequest r = new PaymentRequest();
        r.setIdempotencyKey(idempotencyKey);
        r.setAmount(new BigDecimal("100.00"));
        r.setCurrency("MZN");
        r.setPhoneNumber("+258840000000");
        r.setDescription("Test payment");
        return r;
    }

    private Payment buildPayment(PaymentRequest request) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(request.getIdempotencyKey())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .phoneNumber(request.getPhoneNumber())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private PaymentEvent buildEvent(Payment payment) {
        return PaymentEvent.builder()
                .paymentId(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .phoneNumber(payment.getPhoneNumber())
                .status(PaymentStatus.PENDING)
                .build();
    }
}
