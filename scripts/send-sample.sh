#!/usr/bin/env bash
# Send a few sample transactions into Kafka (requires docker compose up).
set -euo pipefail

send() {
  echo "$2:$1" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic txn-events \
    --property parse.key=true --property key.separator=:
}

now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
send "{\"eventId\":\"$(uuidgen)\",\"userId\":\"u1\",\"deviceId\":\"d1\",\"amount\":100,\"currency\":\"CNY\",\"ip\":\"1.1.1.1\",\"country\":\"CN\",\"occurredAt\":\"$now\"}" u1
send "{\"eventId\":\"$(uuidgen)\",\"userId\":\"u2\",\"deviceId\":\"d2\",\"amount\":30000,\"currency\":\"CNY\",\"ip\":\"2.2.2.2\",\"country\":\"CN\",\"occurredAt\":\"$now\"}" u2
send "{\"eventId\":\"$(uuidgen)\",\"userId\":\"u_blacklisted\",\"deviceId\":\"d3\",\"amount\":50,\"currency\":\"CNY\",\"ip\":\"3.3.3.3\",\"country\":\"CN\",\"occurredAt\":\"$now\"}" u_blacklisted

echo "Sent. Read decisions with:"
echo "docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic risk-decisions --from-beginning"
