package com.whu.graduation.taskincentive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

public class KafkaConfig {
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(2000L, 3)
        );
    }
}
