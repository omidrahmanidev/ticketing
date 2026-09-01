package ro.midra.ticketing.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic seatEventsTopic() {
        return TopicBuilder.name("seat-events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seatHoldCommandsTopic() {
        return TopicBuilder.name("seat-hold-commands")
                .partitions(6)
                .replicas(1)
                .build();
    }
}
