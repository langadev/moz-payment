package com.langa.dev.payment_service.messaging.publisher;

import com.langa.dev.payment_service.messaging.config.RabbitMQConfig;
import com.langa.dev.payment_service.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishProcessing(PaymentEvent event) {
        publish(RabbitMQConfig.RK_PROCESSING, event);
    }

    public void publishCompleted(PaymentEvent event) {
        publish(RabbitMQConfig.RK_COMPLETED, event);
    }

    public void publishFailed(PaymentEvent event) {
        publish(RabbitMQConfig.RK_FAILED, event);
    }

    private void publish(String routingKey, PaymentEvent event) {
        log.info("Publishing payment event: paymentId={} routingKey={} status={}",
                event.paymentId(), routingKey, event.status());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event);
    }
}
