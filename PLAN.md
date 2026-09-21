# SentinelBank: 12-Day Solo Build Plan

## Context
Build the SentinelBank microservices architecture (Spring Boot + Kafka + Angular + Spring AI) alone in 12 days at 3-4 focused hrs/day, which is about 42 hours total. The full diagram (8 services + gateway + Angular x2 + 3 DBs + Kafka + ELK/Zipkin + K8s + LLM) is roughly 3x that. So the plan **protects the correctness core** (transfer saga, outbox, idempotency, optimistic locking, DLT) and cuts or simplifies everything else. Kubernetes is cut first, per the user.

## Scope decisions

### Must have (the product's reason to exist)
API Gateway, Auth, Account, Transaction, Partner Bank (mock), Fraud, Notification, Audit, Kafka with outbox/idempotent consumers/retry/DLT, Angular customer app, Docker Compose one-command startup.

### Simplified (same idea, less work)
| Diagram | MVP | Why |
|---|---|---|
| 3 DB engines | **PostgreSQL** (all relational services, one instance, one schema per service) + **MongoDB** (fraud cases, audit) | Drops MySQL. DB-per-service is preserved logically. |
| Two Angular apps | **One Angular app**, with a role-guarded `/analyst` route | Half the frontend setup |
| Eureka | Compose DNS / static gateway routes | Saves a service |
| Config server / ConfigMaps | `application.yml` + env vars | |
| Fraud GraphQL | REST | |
| ELK + Zipkin | Structured JSON logs with correlation ID; Zipkin only if spare time | |
| Rate limiting | In-memory Bucket4j filter at the gateway (no Redis) | |
| Outbox | Polling publisher (`@Scheduled`), as in the diagram | |

### Cut first, in this order
1. **Kubernetes** (Compose is the deploy target; K8s manifests only as a Day 12 bonus)
2. Zipkin/ELK/Grafana
3. AI Agent Ollama polish (keep a thin read-only endpoint)
4. Separate analyst dashboard

## Key design decisions (fix these on Day 1, don't revisit)
- **Stack:** Java 25 (matches the installed JDK), Spring Boot 4.1.1 (the only stable line Initializr offers now; 3.x is gone), Maven wrapper per service plus a root aggregator pom, Spring Cloud Gateway (reactive), Spring Data JPA + Flyway, Spring Kafka, Resilience4j, Testcontainers, Angular (latest), Kafka in KRaft mode (single broker).
- **Transfer saga (orchestrated by Transaction Service):**
  1. `POST /transfers` with an `Idempotency-Key` header. Store the key plus a request hash; a replay returns the stored response.
  2. Transaction creates a row with state `PENDING`, calls Account **synchronously** (Resilience4j) to debit. The debit is idempotent by `transactionId`. State becomes `DEBITED`, and an **outbox row** `transfer.initiated` is written in the same DB transaction.
  3. The outbox poller publishes to Kafka, **key = accountId**.
  4. Partner Bank consumes it (idempotent by eventId), credits, and emits `transfer.completed` or `transfer.failed`. It has a configurable failure switch for demos.
  5. Transaction consumes the result. `COMPLETED`, or `FAILED` followed by a compensating credit-back to Account (the "undo").
- **Fraud is asynchronous:** it consumes `transfer.initiated` and opens cases (rules: amount threshold, velocity, round-amount, new beneficiary). It flags rather than blocks. State this honestly in the README.
- **Topics:** `transfer.initiated`, `transfer.completed`, `transfer.failed`, each with `.retry` and `.dlt` (Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`).
- **Idempotent consumers:** a shared `processed_events(event_id)` table pattern in a `common` module.
- **Common module:** correlation-ID filter, event envelope (`eventId`, `type`, `occurredAt`, `correlationId`), error model, MDC logging.
- **Auth:** JWT (HS256 shared secret is fine for MVP), refresh tokens, roles `CUSTOMER` / `ANALYST`. The gateway validates the JWT and forwards user headers.
- **AI Agent:** Spring AI + Ollama, with read-only tools (`getBalance`, `getRecentTransactions`), PII masking before the prompt, and an output validator (rejects anything that looks like an instruction or action). No write tools at all.

## Day-by-day (about 3.5 hrs/day)

| Day | Focus | Done when |
|---|---|---|
| **1** | **Foundation.** Monorepo, `docker-compose.yml` (Postgres, Mongo, Kafka), `common` module, empty service skeletons, health endpoints. | `docker compose up` gives a healthy infra; every service boots |
| **2** | **Auth + Gateway.** Register/login/refresh, JWT, roles; gateway routing, JWT check, correlation ID, in-memory rate limit. | Login via gateway, protected route rejects a bad token |
| **3** | **Account Service.** Accounts, balances, ledger entries, `@Version`, idempotent debit/credit by `transactionId`, seed data. | Concurrent debit test shows no lost update |
| **4** | **Transaction Service (part 1).** `POST /transfers`, idempotency-key table, saga state machine, sync debit call with Resilience4j, outbox table. | Duplicate key returns the same response; debit happens once |
| **5** | **Outbox publisher + Kafka.** Poller, topics with key=accountId, event envelope, publish `transfer.initiated`. | Transfer request produces exactly one event |
| **6** | **Partner Bank + saga completion.** Idempotent consumer, credit, `completed`/`failed` events, compensation, retry + DLT. **Go/no-go checkpoint.** | Happy path and failure-undo both work end to end |
| **7** | **Fraud Service.** Idempotent consumer, 3-4 rules, cases in Mongo, `GET /cases`, analyst-only. | Big/rapid transfers create cases; replaying an event creates no duplicate |
| **8** | **Notification + Audit.** Notification consumer (log/MailHog) with retry topic; Audit append-only consumer into Mongo, no update/delete endpoints. | Every transfer leaves an audit trail and a notification |
| **9** | **Angular app.** Login, accounts, transfer form (generates the idempotency key), history, `/analyst` cases view. | Full flow driven from the UI |
| **10** | **Hardening + tests.** Testcontainers integration tests: idempotent replay, optimistic-lock concurrency, outbox-to-Kafka, saga failure/undo. Resilience4j tuning. | Tests green; kill Partner Bank and see the DLT/compensation behave |
| **11** | **AI Agent (thin).** Spring AI + Ollama, read-only tools, PII masking, output validator, chat box in Angular. **Also the buffer day.** | Advice endpoint answers with masked data. If behind, spend this day on Days 6-10 slippage instead |
| **12** | **Polish + ship.** README with the architecture diagram, one-command `docker compose up`, seeded demo users, demo script (happy path, duplicate, failure/undo, fraud case). Bonus only if ahead: K8s manifests, Zipkin. | A cold clone runs and the demo script works |

## Checkpoints (the cut line)
- **End of Day 6:** if the saga (including undo) isn't working, stop adding features. Days 7-8 shrink to the simplest possible consumers.
- **End of Day 9:** if the UI isn't usable, drop the analyst view and use Postman for fraud cases.
- **Day 11:** the AI Agent is the first thing to shrink if the schedule slips.

## Risks
- **Kafka/Compose fiddliness** (Day 1, Day 5): pin image versions, keep one broker, add health checks.
- **Saga edge cases** (Day 6): the sync debit plus outbox is not atomic. It is made safe by the `transactionId`-idempotent debit and by `PENDING`/`DEBITED` state, so a crash mid-way can be retried. Write this down in the README.
- **Ollama on the dev machine:** model size and speed vary. Use a small model (e.g. a 3B-class one), and make the endpoint work with a stub when Ollama isn't running.
- **Scope creep:** anything not in the "Must have" table waits until Day 12.

## Verification (end to end)
1. `docker compose up` on a clean clone starts everything.
2. Register, login, and see a JWT through the gateway.
3. Transfer succeeds: balances change once, the audit event exists, a notification is logged.
4. Repeat the same request with the same `Idempotency-Key`: same response, no second debit.
5. Turn on the Partner Bank failure switch: the transfer ends `FAILED` and the debit is reversed.
6. Send a large or rapid transfer: a fraud case appears in the analyst view; replaying the event creates no duplicate.
7. Force a poison message: it lands in the DLT after retries.
8. Run the concurrent debit test: the balance stays consistent.
9. Ask the AI agent about recent spending: it answers read-only, with PII masked.

## Suggested next step after approval
Start Day 1: create the monorepo structure and `docker-compose.yml`. Need a project folder chosen first, since this session is in a scratch workspace.
