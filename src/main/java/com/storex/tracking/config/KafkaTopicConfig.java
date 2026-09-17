package com.storex.tracking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Cấu hình topic "order-tracking" với chính xác 5 Partitions theo đặc tả bài toán.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_TRACKING_TOPIC = "order-tracking";
    public static final int TOTAL_PARTITIONS = 5;

    @Bean
    public NewTopic orderTrackingTopic() {
        return TopicBuilder.name(ORDER_TRACKING_TOPIC)
                .partitions(TOTAL_PARTITIONS)
                .replicas(1)
                .build();
    }
}
