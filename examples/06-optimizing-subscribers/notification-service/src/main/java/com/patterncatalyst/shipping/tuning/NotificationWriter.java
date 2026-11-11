package com.patterncatalyst.shipping.tuning;

import java.time.Duration;
import java.time.Instant;

import org.apache.camel.Header;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/**
 * Stores the notification for a record. The row is keyed by group, topic, partition and offset, so
 * a record that is delivered again updates its row and adds one to {@code attempts}.
 */
@ApplicationScoped
@Named("notificationWriter")
public class NotificationWriter {

    @ConfigProperty(name = "notify.group")
    String group;
    @ConfigProperty(name = "notify.client")
    String replica;

    @Inject
    MeterRegistry registry;

    /** How long a record waited between being written to Kafka and being stored. */
    private Timer eventAge;

    @PostConstruct
    void registerTimer() {
        eventAge = Timer.builder("edp.notification.event.age")
                .description("Time from the Kafka record timestamp to the stored notification")
                .register(registry);
    }

    @Transactional
    public void write(NotificationRequest request,
            @Header(KafkaConstants.TOPIC) String topic,
            @Header(KafkaConstants.PARTITION) int partition,
            @Header(KafkaConstants.OFFSET) long offset,
            @Header(KafkaConstants.TIMESTAMP) long timestamp) {
        NotificationRow.getEntityManager().createNativeQuery("""
                INSERT INTO notifications.notification
                    (group_id, topic, kafka_partition, kafka_offset, order_id, event_type,
                     attempts, consumer, replica, event_time, processed_at)
                VALUES (:group, :topic, :partition, :offset, :orderId, :eventType,
                        1, :consumer, :replica, to_timestamp(:timestamp / 1000.0), clock_timestamp())
                ON CONFLICT (group_id, topic, kafka_partition, kafka_offset)
                DO UPDATE SET attempts = notification.attempts + 1,
                              consumer = EXCLUDED.consumer,
                              replica = EXCLUDED.replica,
                              processed_at = EXCLUDED.processed_at
                """)
                .setParameter("group", group)
                .setParameter("topic", topic)
                .setParameter("partition", partition)
                .setParameter("offset", offset)
                .setParameter("orderId", request.orderId())
                .setParameter("eventType", request.eventType())
                .setParameter("consumer", Thread.currentThread().getName())
                .setParameter("replica", replica)
                .setParameter("timestamp", (double) timestamp)
                .executeUpdate();
        eventAge.record(Duration.between(Instant.ofEpochMilli(timestamp), Instant.now()));
    }
}
