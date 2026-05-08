package com.langa.dev.payment_service.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "payment.exchange";

    public static final String QUEUE_PROCESSING     = "payment.processing";
    public static final String QUEUE_NOTIFICATIONS  = "payment.notifications";
    public static final String QUEUE_DEAD_LETTER    = "payment.dead-letter";

    public static final String RK_PROCESSING    = "payment.processing";
    public static final String RK_COMPLETED     = "payment.completed";
    public static final String RK_FAILED        = "payment.failed";

    @Bean
    TopicExchange paymentExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    Queue processingQueue() {
        return QueueBuilder.durable(QUEUE_PROCESSING)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QUEUE_DEAD_LETTER)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DEAD_LETTER).build();
    }

    @Bean
    Binding bindingProcessing(Queue processingQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(processingQueue).to(paymentExchange).with(RK_PROCESSING);
    }

    @Bean
    Binding bindingCompleted(Queue notificationsQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(notificationsQueue).to(paymentExchange).with(RK_COMPLETED);
    }

    @Bean
    Binding bindingFailed(Queue notificationsQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(notificationsQueue).to(paymentExchange).with(RK_FAILED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
