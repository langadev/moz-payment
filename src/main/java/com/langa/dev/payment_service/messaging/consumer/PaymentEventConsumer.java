package com.langa.dev.payment_service.messaging.consumer;

import com.langa.dev.payment_service.domain.service.PaymentService;
import com.langa.dev.payment_service.messaging.config.RabbitMQConfig;
import com.langa.dev.payment_service.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PROCESSING)
    public void handleProcessing(PaymentEvent event) {
        MDC.put("paymentId", event.paymentId().toString());
        log.info("Received payment for processing: paymentId={} amount={} {}",
                event.paymentId(), event.amount(), event.currency());
        try {
            paymentService.processPayment(event);
        } finally {
            MDC.remove("paymentId");
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATIONS)
    public void handleNotification(PaymentEvent event) {
        log.info("Received payment notification: paymentId={} status={} phone={}",
                event.paymentId(), event.status(), event.phoneNumber());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DEAD_LETTER)
    public void handleDeadLetter(PaymentEvent event) {
        log.error("Payment event in dead-letter queue: paymentId={} status={} reason={}",
                event.paymentId(), event.status(), event.failureReason());
    }
}
