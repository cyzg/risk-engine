package com.example.risk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    /**
     * Pool for I/O-bound feature lookups. Bounded queue gives back-pressure; CallerRunsPolicy
     * slows the Kafka consumer thread down instead of dropping work when saturated.
     */
    @Bean(name = "featureExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor featureExecutor(
            @Value("${risk.feature.pool.core:16}") int core,
            @Value("${risk.feature.pool.max:32}") int max,
            @Value("${risk.feature.pool.queue:1000}") int queue) {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "feature-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(core, max, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), tf, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
