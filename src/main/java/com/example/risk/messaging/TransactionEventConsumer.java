package com.example.risk.messaging;

import com.example.risk.model.DecisionResult;
import com.example.risk.model.TransactionEvent;
import com.example.risk.service.DecisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Input topic is partitioned by userId, so all events of one user are handled in order by one
 * consumer thread. Offsets are committed manually after the decision is published
 * (at-least-once); duplicates are absorbed by {@link IdempotencyGuard}.
 * Exceptions propagate to the container's DefaultErrorHandler (retry, then dead-letter topic).
 */
@Component
public class TransactionEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final ObjectMapper mapper;
    private final DecisionService service;
    private final IdempotencyGuard guard;
    private final KafkaTemplate<String, String> kafka;
    private final String decisionTopic;

    public TransactionEventConsumer(ObjectMapper mapper, DecisionService service, IdempotencyGuard guard,
                                    KafkaTemplate<String, String> kafka,
                                    @Value("${risk.topics.decisions}") String decisionTopic) {
        this.mapper = mapper;
        this.service = service;
        this.guard = guard;
        this.kafka = kafka;
        this.decisionTopic = decisionTopic;
    }

    @KafkaListener(topics = "${risk.topics.input}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        TransactionEvent event = mapper.readValue(record.value(), TransactionEvent.class);

        if (!guard.firstTime(event.eventId())) {
            log.debug("duplicate event {}, skipped", event.eventId());
            ack.acknowledge();
            return;
        }
        try {
            DecisionResult result = service.decide(event);
            kafka.send(decisionTopic, event.userId(), mapper.writeValueAsString(result)).get();
            ack.acknowledge();
        } catch (Exception ex) {
            guard.release(event.eventId()); // allow reprocessing on retry
            throw ex;
        }
    }
}
