# ADR-003 — Idempotência via Idempotency-Key

**Data:** 2026-05-08  
**Status:** Aceite

## Contexto

Em redes instáveis (contexto Moçambique), um cliente pode reenviar o mesmo pedido de pagamento por timeout ou erro de rede antes de receber a resposta. Sem idempotência, isso resultaria em pagamentos duplicados — um problema crítico em sistemas financeiros.

## Decisão

Implementar idempotência ao nível da aplicação com um campo `idempotency_key` obrigatório no corpo do `POST /payments`, com unicidade garantida por `UNIQUE constraint` na base de dados.

```
POST /api/v1/payments
{
  "idempotencyKey": "cliente-uuid-gerado-pelo-cliente",
  ...
}
```

Se um pedido com o mesmo `idempotencyKey` já existir, a API retorna `200 OK` com o pagamento original (em vez de `201 Created`). O cliente pode distinguir pelo status HTTP se o pagamento foi criado agora ou recuperado.

## Razões

- **Segurança ao nível da base de dados** — o `UNIQUE constraint` em `idempotency_key` evita race conditions mesmo com múltiplos pods a processar pedidos em simultâneo. A aplicação verifica primeiro (`findByIdempotencyKey`), mas a constraint é a última linha de defesa.
- **Sem estado no servidor** — não é necessária cache distribuída (Redis) ou tabela separada de "pedidos processados"; a própria tabela `payments` serve de registo.
- **Comportamento previsível** — retornar o pagamento existente com `200` é mais correcto do que `201` ou erro, pois o estado é consistente independentemente de quantas vezes o cliente reenviar.
- **Rastreabilidade** — o `idempotencyKey` gerado pelo cliente (normalmente um UUID v4) permite correlacionar logs do cliente com logs do servidor.

## Alternativas consideradas

| Alternativa | Razão de rejeição |
|---|---|
| Header `Idempotency-Key` (estilo Stripe) | Requer middleware extra para extrair e indexar o header; mais complexo sem benefício adicional para este MVP |
| Redis para deduplicação | Adiciona dependência de infra; TTL pode expirar e permitir duplicados após longa espera |
| Verificação apenas na aplicação (sem constraint DB) | Race condition possível com múltiplos pods; não é seguro |

## Consequências

- O cliente **é responsável por gerar** um `idempotencyKey` único (recomendado: UUID v4) por operação de pagamento.
- Reutilizar o mesmo `idempotencyKey` com dados diferentes (amount, currency) retorna o pagamento original sem erro — comportamento documentado no Swagger.
- O campo é indexado implicitamente pelo `UNIQUE constraint`, tornando as buscas por `idempotencyKey` eficientes.
