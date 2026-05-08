# Arquitectura — Payment Service

## Visão Geral

Sistema de pagamentos resiliente desenhado para o contexto de Moçambique: baixa conectividade, integrações com provedores externos (MPesa, e-Mola) e necessidade de alta disponibilidade mesmo em condições de rede instável.

---

## Diagrama de Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                        Cliente / App Mobile                      │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster (VPS)                       │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │               payment-service (Pod x N)                  │    │
│  │                                                           │    │
│  │  REST API          Domain           Messaging             │    │
│  │  ──────────        ──────           ─────────             │    │
│  │  POST /payments    PaymentService   Publisher             │    │
│  │  GET  /payments    PaymentRepository Consumer             │    │
│  │  POST /webhooks    PaymentEvent     DLQ Handler           │    │
│  │                                                           │    │
│  │  Observability                                            │    │
│  │  ─────────────                                            │    │
│  │  /actuator/health                                         │    │
│  │  /actuator/prometheus                                     │    │
│  └──────┬──────────────────────────┬────────────────────────┘    │
│         │                          │                              │
│         ▼                          ▼                              │
│  ┌─────────────┐          ┌───────────────────┐                  │
│  │  PostgreSQL │          │     RabbitMQ       │                  │
│  │  (StatefulSet)│         │  payment.exchange  │                  │
│  │             │          │  ├ processing       │                  │
│  │  payments   │          │  ├ notifications    │                  │
│  │  (table)    │          │  └ dead-letter      │                  │
│  └─────────────┘          └───────────────────┘                  │
│                                                                   │
│  ┌──────────────────────────────────────────┐                    │
│  │   Prometheus + Grafana (Observabilidade) │                    │
│  └──────────────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
                             │
                             │ Webhooks (HTTPS)
                             ▼
                   ┌──────────────────┐
                   │ Provedor Externo  │
                   │ (MPesa / e-Mola)  │
                   └──────────────────┘
```

---

## Fluxo de Pagamento

```
Cliente                  API                  RabbitMQ           Provedor
  │                       │                      │                   │
  │  POST /payments        │                      │                   │
  │───────────────────────►│                      │                   │
  │                       │  1. Verifica          │                   │
  │                       │     idempotency_key   │                   │
  │                       │  2. Salva PENDING     │                   │
  │                       │  3. Publica evento    │                   │
  │                       │──────────────────────►│                   │
  │  201 PENDING           │                      │                   │
  │◄───────────────────────│                      │                   │
  │                       │                      │  4. Consumer        │
  │                       │                      │     processa        │
  │                       │                      │────────────────────►│
  │                       │                      │  5. Salva           │
  │                       │                      │     PROCESSING      │
  │                       │                      │◄────────────────────│
  │                       │                      │  (ref. do provedor) │
  │                       │                      │                   │
  │  POST /webhooks/provider (do provedor)        │                   │
  │───────────────────────►│                      │                   │
  │                       │  6. Actualiza         │                   │
  │                       │     COMPLETED/FAILED  │                   │
  │                       │  7. Publica notif.   │                   │
  │  200 OK                │──────────────────────►│                   │
  │◄───────────────────────│                      │                   │
```

---

## Componentes

### API Layer (`api/`)
- **PaymentController** — `POST /api/v1/payments`, `GET /api/v1/payments/{id}`
- **WebhookController** — `POST /webhooks/provider` (callback do provedor)
- **GlobalExceptionHandler** — mapeia excepções para ProblemDetail (RFC 9457)
- **DTOs** — `PaymentRequest`, `WebhookRequest`, `PaymentResponse`

### Domain Layer (`domain/`)
- **PaymentService** — orquestra criação, processamento e webhook
- **Payment** — entidade JPA com auditoria automática (`@CreatedDate`, `@LastModifiedDate`)
- **PaymentStatus** — `PENDING → PROCESSING → COMPLETED | FAILED | CANCELLED`
- **PaymentRepository** — queries por `id`, `idempotencyKey`, `providerRef`, status+retryCount

### Infrastructure Layer (`infrastructure/`)
- **PaymentProvider** — interface de abstracção para provedores externos
- **MockPaymentProvider** — implementação mock para desenvolvimento e testes

### Messaging Layer (`messaging/`)
- **RabbitMQConfig** — declaração de exchanges, queues e bindings
- **PaymentEventPublisher** — publica eventos `processing`, `completed`, `failed`
- **PaymentEventConsumer** — consome `processing`, `notifications`, `dead-letter`

---

## Estratégias de Resiliência

### Retry com Backoff Exponencial
```
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
```
Tentativas: 1s → 2s → 4s → DLQ

### Dead-Letter Queue
Pagamentos que esgotam retries ficam na `payment.dead-letter` com o motivo de falha. Um job de retry agendado pode reprocessá-los.

### Idempotência
`UNIQUE constraint` em `idempotency_key` previne pagamentos duplicados mesmo com múltiplos pods e retransmissões de rede.

### Mensagens Duráveis
Todas as queues são `durable: true`. Mensagens sobrevivem a reinicios do RabbitMQ.

---

## Observabilidade

| Componente | Endpoint / Mecanismo |
|---|---|
| Health check | `GET /actuator/health` |
| Métricas Prometheus | `GET /actuator/prometheus` |
| Counter `payments.created` | Incrementado por cada pagamento processado com sucesso |
| Counter `payments.provider.failed` | Incrementado por cada falha permanente do provedor |
| MDC `paymentId` | Presente em todos os logs dentro do contexto de um pagamento |
| Logs JSON (prod) | Via `logstash-logback-encoder` com profile `prod` |

### SLIs/SLOs sugeridos
- **Disponibilidade:** 99.5% (uptime do endpoint `POST /payments`)
- **Latência p99:** < 500ms para `POST /payments` (excluindo tempo do provedor)
- **Taxa de erro:** < 1% de respostas 5xx

---

## Segurança

- Container Docker executa como utilizador não-root (`appuser`)
- Stack traces nunca expostos na resposta HTTP (`include-stacktrace: never`)
- Credenciais injectadas via variáveis de ambiente / Kubernetes Secrets
- Validação de input com Bean Validation (`@NotBlank`, `@DecimalMin`, `@Size`)

---

## Decisões de Arquitectura

Ver pasta [`docs/adr/`](adr/) para os ADRs detalhados:

- [ADR-001](adr/ADR-001-postgresql.md) — PostgreSQL como base de dados principal
- [ADR-002](adr/ADR-002-rabbitmq.md) — RabbitMQ para processamento assíncrono
- [ADR-003](adr/ADR-003-idempotencia.md) — Idempotência via Idempotency-Key
