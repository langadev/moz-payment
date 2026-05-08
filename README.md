# Payment Service

> Sistema de pagamentos resiliente para Moçambique — construído com **Spring Boot 3**, **PostgreSQL** e **RabbitMQ**.

---

##  Índice

- [Requisitos](#-requisitos)
- [Execução local](#-execução-local)
- [Endpoints](#-endpoints)
- [Testes](#-testes)
- [Docker](#-docker)
- [Kubernetes](#-kubernetes)
- [Observabilidade](#-observabilidade)
- [Arquitectura](#-arquitectura)

---

##  Requisitos

| Ferramenta | Versão mínima |
|:---|:---:|
|  Java | `17` |
|  Maven | `3.9+` *(ou use o `mvnw` incluído)* |
|  Docker | `24+` |
|  Docker Compose | `v2` |
|  kubectl | `1.28+` *(para deploy K8s)* |

---

##  Execução local

### 1. Subir infraestrutura (PostgreSQL + RabbitMQ)

```bash
docker compose up -d postgres rabbitmq
```

### 2. Iniciar a aplicação

```bash
./mvnw spring-boot:run
```

> A aplicação estará disponível em `http://localhost:8080`

### 3. Tudo de uma vez (app + infra)

```bash
docker compose up -d
```

---

##  Endpoints

###  Criar pagamento

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "uuid-gerado-pelo-cliente",
    "amount": 150.00,
    "currency": "MZN",
    "phoneNumber": "+258840000000",
    "description": "Pagamento de serviço"
  }'
```

**Resposta `201 Created`:**

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "idempotencyKey": "uuid-gerado-pelo-cliente",
  "amount": 150.00,
  "currency": "MZN",
  "status": "PENDING"
}
```

>  **Idempotência:** Reenviar o mesmo `idempotencyKey` retorna `200 OK` com o pagamento original — sem duplicação.

---

###  Consultar pagamento

```bash
curl http://localhost:8080/api/v1/payments/{id}
```

---

###  Webhook do provedor

```bash
curl -X POST http://localhost:8080/webhooks/provider \
  -H "Content-Type: application/json" \
  -d '{
    "providerRef": "MOCK-XXXXXXXX",
    "status": "COMPLETED"
  }'
```

> Status válidos para webhook: `COMPLETED` • `FAILED`

---

###  Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

##  Testes

### Testes unitários

```bash
./mvnw test -Dtest="PaymentServiceTest"
```

### Testes de integração *(requer Docker)*

```bash
./mvnw verify -Dtest="PaymentIntegrationTest"
```

>  Os testes de integração usam **Testcontainers** — sobem automaticamente PostgreSQL e RabbitMQ em containers Docker durante a execução.

### Todos os testes

```bash
./mvnw verify
```

---

##  Docker

### Build da imagem

```bash
./mvnw package -DskipTests
docker build -t payment-service:latest .
```

### Execução standalone

```bash
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/payment_db \
  -e SPRING_DATASOURCE_USERNAME=payment_user \
  -e SPRING_DATASOURCE_PASSWORD=payment_secret \
  -e RABBITMQ_HOST=host.docker.internal \
  payment-service:latest
```

---

##  Kubernetes

### Pré-requisitos

- Cluster Kubernetes acessível — verifique com `kubectl cluster-info`
- Namespace criado:

```bash
kubectl create namespace payments
```

### Deploy

```bash
# 1. Secrets (edite os valores antes de aplicar)
kubectl apply -f k8s/secret.yaml -n payments

# 2. ConfigMap, Deployment e Service
kubectl apply -f k8s/ -n payments

# 3. Verificar estado
kubectl get pods -n payments
kubectl get svc -n payments
```

### Verificar logs

```bash
kubectl logs -l app=payment-service -n payments -f
```

---

##  Observabilidade

| Endpoint | Descrição |
|:---|:---|
| `GET /actuator/health` | Health check *(liveness/readiness)* |
| `GET /actuator/prometheus` | Métricas no formato Prometheus |

###  Métricas disponíveis

| Métrica | Descrição |
|:---|:---|
| `payments_created_total` | Total de pagamentos processados com sucesso pelo provedor |
| `payments_provider_failed_total` | Total de falhas permanentes do provedor |

###  Logs estruturados *(produção)*

Activar o profile `prod` para logs em JSON *(formato Logstash)*:

```bash
java -jar app.jar --spring.profiles.active=prod
```

---

##  Arquitectura

> Ver [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) para o diagrama completo e descrição dos componentes.

###  ADRs *(Architecture Decision Records)*

| ADR | Decisão |
|:---|:---|
| [ADR-001](docs/adr/ADR-001-postgresql.md) | PostgreSQL como base de dados principal |
| [ADR-002](docs/adr/ADR-002-rabbitmq.md) | RabbitMQ para processamento assíncrono |
| [ADR-003](docs/adr/ADR-003-idempotencia.md) | Idempotência via Idempotency-Key |

---

##  Ciclo de vida do pagamento

```
PENDING ──→ PROCESSING ──→ COMPLETED
                      └──→ FAILED
```

| Status | Descrição |
|:---:|:---|
| `PENDING` | Criado, aguarda processamento pelo provedor |
| `PROCESSING` | Enviado ao provedor, aguarda confirmação |
| `COMPLETED` | ✅ Confirmado pelo provedor via webhook |
| `FAILED` | ❌ Rejeitado pelo provedor ou sem resposta após retries |