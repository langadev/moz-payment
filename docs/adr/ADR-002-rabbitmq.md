# ADR-002 — RabbitMQ para processamento assíncrono de pagamentos

**Data:** 2026-05-08  
**Status:** Aceite

## Contexto

O processamento de pagamentos em Moçambique envolve chamadas a provedores externos (MPesa, e-Mola) que podem ter latência elevada ou falhar intermitentemente devido a baixa conectividade. Processar de forma síncrona na thread HTTP bloquearia o cliente e reduziria a resiliência do sistema.

## Decisão

Usar **RabbitMQ** com um Topic Exchange (`payment.exchange`) e três filas:

| Fila | Propósito |
|---|---|
| `payment.processing` | Envio ao provedor externo |
| `payment.notifications` | Notificações pós-processamento |
| `payment.dead-letter` | Mensagens que esgotaram retries |

## Razões

- **Desacoplamento** — o endpoint `POST /payments` responde imediatamente com `201 PENDING`; o processamento pesado ocorre fora da thread HTTP.
- **Resiliência** — mensagens são persistidas em disco (`durable: true`); se a aplicação reiniciar, nenhuma mensagem é perdida.
- **Dead-letter queue** — pagamentos que falham após 3 retries (com backoff exponencial via `@Retryable`) caem na DLQ para análise e reprocessamento manual ou automatizado.
- **Spring AMQP** — integração nativa com Spring Boot, incluindo `Jackson2JsonMessageConverter` para serialização JSON transparente.
- **Operacional simplificado** — RabbitMQ é mais simples de operar que Kafka em clusters pequenos; o Management UI facilita monitorização.

## Alternativas consideradas

| Alternativa | Razão de rejeição |
|---|---|
| Kafka | Overhead operacional elevado para o volume inicial; requer ZooKeeper ou KRaft; mais adequado para event streaming em larga escala |
| Processamento síncrono | Bloqueia a thread HTTP; falhas do provedor causam timeout directo para o cliente |
| SQS (AWS) | Vendor lock-in; não adequado para deploy on-premise em VPS |

## Consequências

- O estado de um pagamento pode ser `PENDING` por um curto período após o `POST` — os clientes devem usar `GET /payments/{id}` para polling ou implementar webhooks para notificações em tempo real.
- A fila `payment.dead-letter` requer monitorização activa; alertas devem ser configurados quando mensagens entram nessa fila.
- Em cenários de alta concorrência, o número de consumers pode ser escalado horizontalmente sem alteração ao código (basta adicionar réplicas do pod).
