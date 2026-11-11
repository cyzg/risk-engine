package com.example.risk.sink;

import com.example.risk.model.DecisionResult;
import com.example.risk.model.RuleHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Sinks decisions into ClickHouse in batches. It reads the decision topic with its own consumer
 * group, so a slow or unavailable ClickHouse never affects the decision path; Kafka is the buffer.
 *
 * One poll = one INSERT (ClickHouse prefers few large inserts over many small ones, otherwise
 * it accumulates too many parts). The offset is committed only after the INSERT succeeds; on
 * failure the exception goes to the container's error handler (retry, then dead letter), and
 * any redelivered rows are collapsed by the ReplacingMergeTree table engine.
 */
@Component
public class ClickHouseDecisionSink {
    private static final Logger log = LoggerFactory.getLogger(ClickHouseDecisionSink.class);
    private static final String INSERT = "INSERT INTO risk.decision_log "
            + "(event_id, user_id, decision, shadow, rule_version, hit_rules, risk_score, latency_ms, decided_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Timer insertTimer;
    private final Counter rows;
    private final Counter badRecords;

    public ClickHouseDecisionSink(JdbcTemplate jdbc, ObjectMapper mapper, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.insertTimer = Timer.builder("risk.clickhouse.insert.latency").register(registry);
        this.rows = Counter.builder("risk.clickhouse.rows").register(registry);
        this.badRecords = Counter.builder("risk.clickhouse.bad.records").register(registry);
    }

    @KafkaListener(
            topics = "${risk.topics.decisions}",
            groupId = "risk-engine-sink",
            batch = "true",
            concurrency = "1",
            properties = {
                    "max.poll.records=${risk.sink.clickhouse.max-batch-size}",
                    "fetch.min.bytes=${risk.sink.clickhouse.min-bytes}",
                    "fetch.max.wait.ms=${risk.sink.clickhouse.max-wait-ms}"})
    public void onBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        List<DecisionResult> batch = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            try {
                batch.add(mapper.readValue(record.value(), DecisionResult.class));
            } catch (Exception ex) {
                // A malformed message can never succeed; skip it instead of blocking the partition.
                badRecords.increment();
                log.warn("skip unparsable decision at {}-{}@{}", record.topic(), record.partition(), record.offset());
            }
        }
        if (!batch.isEmpty()) {
            insertTimer.record(() -> insert(batch));
            rows.increment(batch.size());
            log.debug("inserted {} rows into ClickHouse", batch.size());
        }
        ack.acknowledge(); // only reached if the INSERT succeeded
    }

    private void insert(List<DecisionResult> batch) {
        jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DecisionResult d = batch.get(i);
                String[] hitRules = d.hits() == null ? new String[0]
                        : d.hits().stream().map(RuleHit::ruleId).toArray(String[]::new);
                ps.setString(1, d.eventId());
                ps.setString(2, d.userId());
                ps.setString(3, d.decision().name());
                ps.setInt(4, d.shadow() ? 1 : 0);
                ps.setLong(5, d.ruleVersion());
                ps.setArray(6, ps.getConnection().createArrayOf("String", hitRules));
                ps.setInt(7, d.riskScore());
                ps.setLong(8, d.latencyMs());
                ps.setTimestamp(9, Timestamp.from(d.decidedAt()));
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }
}
