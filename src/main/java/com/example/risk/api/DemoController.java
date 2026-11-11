package com.example.risk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Demo helper behind the web UI: submits a transaction to the same Kafka input topic a real
 * upstream system would use, and keeps the latest decisions in memory for display.
 * The decision flow itself is untouched (Kafka -> DecisionService -> Kafka).
 */
@RestController
@RequestMapping("/api")
public class DemoController {
    private static final int MAX_KEPT = 100;

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String inputTopic;
    private final ConcurrentLinkedDeque<JsonNode> recent = new ConcurrentLinkedDeque<>();

    public DemoController(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                          @Value("${risk.topics.input}") String inputTopic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.inputTopic = inputTopic;
    }

    /** Fills in eventId / occurredAt when omitted, then publishes keyed by userId. */
    @PostMapping("/transactions")
    public Map<String, String> submit(@RequestBody ObjectNode body) throws Exception {
        String userId = body.path("userId").asText("");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!body.hasNonNull("eventId") || body.get("eventId").asText().isBlank()) {
            body.put("eventId", UUID.randomUUID().toString());
        }
        if (!body.hasNonNull("occurredAt")) {
            body.put("occurredAt", Instant.now().toString());
        }
        kafka.send(inputTopic, userId, mapper.writeValueAsString(body)).get();
        return Map.of("eventId", body.get("eventId").asText(), "status", "SENT");
    }

    @GetMapping("/decisions")
    public List<JsonNode> decisions() {
        List<JsonNode> list = new ArrayList<>(recent);
        Collections.reverse(list); // newest first
        return list;
    }

    // Own consumer group, only new messages: the UI shows decisions made since it was started.
    @KafkaListener(topics = "${risk.topics.decisions}", groupId = "risk-engine-ui",
            properties = "auto.offset.reset=latest")
    public void onDecision(String value, Acknowledgment ack) throws Exception {
        recent.addLast(mapper.readTree(value));
        while (recent.size() > MAX_KEPT) {
            recent.pollFirst();
        }
        ack.acknowledge();
    }
}
