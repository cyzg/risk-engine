# risk-engine

Real-time anti-fraud decision engine (personal project). Transaction events arrive via Kafka,
are enriched with real-time features from Redis, evaluated by a hot-reloadable rule engine, and
the decision (PASS / REVIEW / REJECT) is published back to Kafka.

```
txn-events (Kafka, key=userId)
   -> TransactionEventConsumer  (manual ack, idempotency, retry + DLT)
   -> DecisionService
        -> RedisFeatureService  (sliding windows via Lua, parallel on bounded pool, timeout + degrade)
        -> RuleEngine           (immutable RuleSet snapshot behind a volatile reference)
   -> risk-decisions (Kafka)    -> [TODO] ClickHouse sink
```

## Run

Only Docker (Desktop) is required; the app is built inside Docker, no local JDK/Maven needed.

```bash
docker compose up -d --build         # app, kafka, redis, postgres, clickhouse, prometheus, grafana
./scripts/send-sample.sh             # push sample events (Git Bash / WSL / macOS / Linux)
docker compose logs -f app           # watch the app
curl localhost:8080/actuator/prometheus | grep risk_
```

Prometheus: http://localhost:9090, Grafana: http://localhost:3000 (admin/admin).

If you have a local JDK 17 + Maven, you can also run only the infra in Docker and the app on the host:
`docker compose up -d kafka redis postgres clickhouse prometheus grafana && mvn spring-boot:run`

Hot-update rules:

```bash
curl -X PUT localhost:8080/admin/rules -H 'Content-Type: application/json' \
  -d '{"reviewAmount":1000,"rejectAmount":8000,"velocityPerMinute":5,"deviceAccountLimit":2,"blacklistedUsers":["u_blacklisted"]}'
```

## Roadmap (what to build next, roughly in order)

1. **ClickHouse sink**: consume `risk-decisions` and batch-insert into `risk.decision_log`.
2. **PostgreSQL persistence**: load rules/blacklist from `rule_config` / `blacklist`, version history, audit.
3. **Integration tests** with Testcontainers (Kafka + Redis): duplicate delivery, DLT, feature timeout degrade.
4. **Load test** (JMH / k6 / custom producer): record QPS and P99 *before/after* tuning the pool and timeouts.
5. **Grafana dashboard**: decision latency P99, decision rate by result, feature degrade count, consumer lag.
6. **Case management**: persist REVIEW decisions to `review_case`; optional Camunda BPMN approval flow.
7. **Rule DSL**: expression-based rules (e.g. SpEL/MVEL) instead of hard-coded classes; shadow/gray rollout per rule.
