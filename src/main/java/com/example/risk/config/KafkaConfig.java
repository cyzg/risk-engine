package com.example.risk.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /** Retry 3 times, 500ms apart, then publish the poison message to <input>.DLT. */
    @Bean
    public CommonErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3));
    }

    @Bean
    public NewTopic inputTopic(@Value("${risk.topics.input}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic decisionTopic(@Value("${risk.topics.decisions}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic deadLetterTopic(@Value("${risk.topics.input}") String name) {
        return TopicBuilder.name(name + ".DLT").partitions(3).replicas(1).build();
    }
}
