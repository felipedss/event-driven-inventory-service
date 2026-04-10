package com.platform.inventoryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Value("${kafka.dlq.partitions}")
  private int dlqPartitions;

  @Value("${kafka.dlq.replicas}")
  private int dlqReplicas;

  @Bean
  public NewTopic inventoryReserveDlq() {
    return TopicBuilder.name("order.inventory.reserve.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }

  @Bean
  public NewTopic inventoryReleaseDlq() {
    return TopicBuilder.name("order.inventory.release.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }
}
