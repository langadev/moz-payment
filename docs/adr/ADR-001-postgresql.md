# ADR-001 — PostgreSQL como base de dados principal

**Data:** 2026-05-08  
**Status:** Aceite

## Contexto

O sistema de pagamentos precisa de persistência transaccional com garantias ACID. Os pagamentos envolvem operações de débito/crédito que não podem ser perdidas nem duplicadas. O ambiente alvo é Moçambique, onde a infraestrutura pode ser limitada — o banco de dados precisa de ser operável com recursos modestos.

## Decisão

Usar **PostgreSQL 17** como única base de dados relacional.

## Razões

- **ACID completo** — transacções atómicas garantem que um pagamento ou é gravado inteiramente ou não é gravado, sem estados intermédios inconsistentes.
- **Suporte nativo a UUID** — o campo `id` é `UUID` gerado pela JPA, sem colisões e sem expor sequências numéricas previsíveis na API.
- **`UNIQUE` constraint em `idempotency_key`** — a unicidade é garantida ao nível da base de dados, não apenas na aplicação, eliminando race conditions em cenários de alta concorrência.
- **Flyway** — migrações versionadas permitem evoluir o schema de forma controlada e reproduzível em todos os ambientes (dev, staging, prod).
- **Ecosistema Java** — integração madura com Spring Data JPA, HikariCP e Testcontainers.
- **Custo operacional** — open-source, bem suportado em Kubernetes via Helm charts e operadores (CloudNativePG).

## Alternativas consideradas

| Alternativa | Razão de rejeição |
|---|---|
| MySQL | Menor suporte a tipos avançados (JSONB, arrays); comportamento de transacções ligeiramente diferente |
| MongoDB | Sem transacções multi-documento no modo standalone; modelo de dados financeiros beneficia de schema rígido |
| H2 (in-memory) | Apenas para testes; sem suporte a produção |

## Consequências

- A aplicação está acoplada ao PostgreSQL; migrar para outro RDBMS requer mudança de dialect Hibernate e validação das migrações Flyway.
- O Testcontainers usa a mesma imagem `postgres:17-alpine` nos testes de integração, garantindo paridade entre test e prod.
