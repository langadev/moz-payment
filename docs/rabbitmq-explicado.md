# RabbitMQ no Payment Service — Explicado do Zero

## O que é o RabbitMQ e por que usá-lo?

Imagine que o seu sistema de pagamentos precisa processar uma transação. Essa transação pode envolver várias etapas: confirmar com o provedor de pagamento, enviar SMS para o cliente, registar no banco de dados, notificar outros sistemas. Se tudo isso for feito de forma direta e sequencial, o cliente fica esperando durante **todas** essas etapas.

O RabbitMQ resolve isso com um conceito simples: **uma fila de mensagens**.

Em vez de fazer tudo na hora, o sistema:
1. Recebe o pedido de pagamento
2. Coloca uma mensagem numa fila ("processar este pagamento")
3. Responde imediatamente ao cliente ("recebemos, estamos a processar")
4. Nos bastidores, outro componente lê a fila e processa devagar

É como deixar uma nota num quadro de avisos. Quem precisa, lê e trata do assunto — sem precisar que você espere.

---

## Conceitos fundamentais (com analogias)

### Exchange — O Carteiro

O **Exchange** é o ponto de entrada de todas as mensagens. Ele **não armazena** mensagens — ele as **distribui** para as filas certas, como um carteiro que lê o endereço e entrega na caixa correta.

No nosso projeto criámos:
```
payment.exchange  (tipo: topic)
```

O tipo **topic** permite distribuir mensagens com base em padrões de texto — como filtros inteligentes. Enviamos uma mensagem com a etiqueta `payment.processing` e o exchange sabe exatamente para qual fila enviar.

---

### Routing Key — O Endereço da Carta

Toda mensagem enviada ao exchange precisa de um **routing key** — é como o endereço na carta. O exchange usa esse endereço para decidir para qual fila a mensagem vai.

No nosso projeto temos três routing keys:

| Routing Key | Significa |
|---|---|
| `payment.processing` | "Este pagamento precisa ser processado" |
| `payment.completed` | "Este pagamento foi concluído com sucesso" |
| `payment.failed` | "Este pagamento falhou" |

---

### Queue — A Caixa de Correio

A **Queue (fila)** é onde as mensagens ficam guardadas até alguém as ler e processar. Ela garante que nenhuma mensagem se perde — se o sistema cair e voltar, as mensagens ainda estão lá.

Criámos três filas:

#### `payment.processing`
Guarda pedidos de pagamentos que precisam ser processados. Um consumer lê esta fila e executa a lógica de pagamento.

#### `payment.notifications`
Guarda eventos de pagamentos concluídos ou falhados. Um consumer lê esta fila e envia notificações (SMS, push, etc.) para o cliente.

#### `payment.dead-letter`
Esta é a fila especial. Se uma mensagem falhar **3 vezes seguidas** (o sistema tentou processar e não conseguiu), ela é automaticamente movida para cá. Assim não se perde nada — fica guardada para análise manual ou reprocessamento posterior.

---

### Binding — A Regra de Entrega

O **Binding** é a regra que liga o Exchange à Queue. É como dizer ao carteiro: "toda carta com endereço `payment.completed` vai para a caixa `payment.notifications`".

As ligações que configurámos:

```
payment.exchange
    ├── routing key "payment.processing"  →  fila: payment.processing
    ├── routing key "payment.completed"   →  fila: payment.notifications
    └── routing key "payment.failed"      →  fila: payment.notifications
```

---

## Os ficheiros que foram criados

### `RabbitMQConfig.java` — A Planta da Arquitectura

Este ficheiro é o "mapa" do RabbitMQ. É aqui que declaramos todas as filas, o exchange e as regras de binding. O Spring lê este ficheiro na inicialização e cria tudo automaticamente no RabbitMQ se ainda não existir.

Pontos importantes:
- As filas `payment.processing` e `payment.notifications` têm configurado o **dead-letter** — ou seja, em caso de falha repetida, a mensagem vai automaticamente para `payment.dead-letter`
- O **MessageConverter** foi configurado para JSON, o que significa que as mensagens são serializadas e deserializadas automaticamente como objetos Java

---

### `PaymentEvent.java` — A Mensagem em Si

Este é o **conteúdo** da mensagem que viaja pelas filas. É um `record` Java (imutável, simples) com os campos relevantes de um pagamento:

```
paymentId       → Identificador único do pagamento
idempotencyKey  → Garante que o mesmo pagamento não é processado duas vezes
amount          → Valor da transação
currency        → Moeda (ex: MZN, USD)
phoneNumber     → Número do cliente (para notificações)
status          → Estado actual (PENDING, COMPLETED, FAILED, etc.)
providerRef     → Referência do provedor de pagamento externo
failureReason   → Motivo da falha, se houver
occurredAt      → Momento em que o evento aconteceu
```

---

### `PaymentEventPublisher.java` — Quem Envia as Mensagens

Este componente é responsável por **publicar** eventos no RabbitMQ. Outros serviços (como o `PaymentService`) vão chamar este publisher quando algo acontece.

Tem três métodos:

```java
publishProcessing(event)  // Envia para processar o pagamento
publishCompleted(event)   // Anuncia que o pagamento foi concluído
publishFailed(event)      // Anuncia que o pagamento falhou
```

Cada método envia a mensagem ao `payment.exchange` com a routing key correcta, e o exchange trata do resto.

---

### `PaymentEventConsumer.java` — Quem Recebe e Processa

Este componente **escuta** as filas e age quando uma mensagem chega. Tem três listeners:

```java
handleProcessing()   // Lê de payment.processing → executa a lógica de pagamento
handleNotification() // Lê de payment.notifications → envia notificações ao cliente
handleDeadLetter()   // Lê de payment.dead-letter → regista erro para análise
```

O `@RabbitListener` é a anotação que diz ao Spring: "quando chegar uma mensagem nesta fila, chama este método automaticamente".

---

## As configurações feitas

### `application.yaml`

```yaml
spring:
  rabbitmq:
    host: localhost       # Endereço do servidor RabbitMQ
    port: 5672            # Porta padrão AMQP
    username: guest       # Utilizador (em produção, usar variáveis de ambiente)
    password: guest
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1000ms  # Espera 1s antes de tentar novamente
          max-attempts: 3           # Máximo de 3 tentativas
          multiplier: 2.0           # Dobra o tempo a cada tentativa (1s → 2s → 4s)
```

O bloco de **retry** é importante: se o processamento de uma mensagem falhar (ex: o banco de dados está indisponível), o sistema tentará automaticamente até 3 vezes antes de mover a mensagem para a dead-letter queue.

---

### `compose.yaml`

Adicionámos o serviço RabbitMQ ao Docker Compose:

```yaml
rabbitmq:
  image: rabbitmq:4-management-alpine
  ports:
    - "5672:5672"   # Porta de comunicação da aplicação com o RabbitMQ
    - "15672:15672" # Porta da interface web de gestão (Management UI)
```

A porta `15672` dá acesso ao **Management UI** — uma interface web onde se pode ver em tempo real:
- Quantas mensagens estão em cada fila
- Taxa de publicação e consumo
- O estado de cada consumer
- Mover mensagens da dead-letter queue manualmente

Acesso: http://localhost:15672 (utilizador: `guest`, senha: `guest`)

---

## O fluxo completo de um pagamento

```
Cliente faz pedido de pagamento
        │
        ▼
  API recebe o pedido
        │
        ▼
  PaymentEventPublisher.publishProcessing(event)
        │
        ▼
  payment.exchange ──[routing key: payment.processing]──▶ fila: payment.processing
        │
        ▼
  PaymentEventConsumer.handleProcessing()
  (processa o pagamento com o provedor)
        │
        ├── Sucesso ──▶ PaymentEventPublisher.publishCompleted(event)
        │                       │
        │               payment.exchange ──[payment.completed]──▶ fila: payment.notifications
        │                       │
        │               PaymentEventConsumer.handleNotification()
        │               (envia SMS / push ao cliente)
        │
        └── Falha ──▶ PaymentEventPublisher.publishFailed(event)
                                │
                        payment.exchange ──[payment.failed]──▶ fila: payment.notifications
                                │
                        PaymentEventConsumer.handleNotification()
                        (notifica o cliente da falha)
```

Se o `handleProcessing()` lançar uma excepção, o retry entra em acção (até 3x). Se continuar a falhar, a mensagem vai para `payment.dead-letter` e é registado um erro para análise.

---

## Por que esta arquitectura é boa para um sistema de pagamentos?

| Problema sem RabbitMQ | Solução com RabbitMQ |
|---|---|
| Cliente espera que tudo processe na hora | Resposta imediata; processamento em background |
| Se um serviço cair, a mensagem perde-se | Mensagens persistidas na fila até serem processadas |
| Se o sistema de notificações cair, o pagamento falha | São sistemas independentes; o pagamento não depende da notificação |
| Difícil escalar o processamento | Basta adicionar mais consumers para a mesma fila |
| Sem visibilidade de erros | Dead-letter queue guarda tudo que falhou para análise |
