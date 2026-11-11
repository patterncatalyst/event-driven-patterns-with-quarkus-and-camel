package com.patterncatalyst.shipping.publishing;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.kafka.DefaultKafkaClientFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Camel creates its own Kafka clients, and neither Camel nor the Quarkus Micrometer binder observes them.
 * This factory binds Micrometer's Kafka client metrics to every producer Camel creates and unbinds them
 * when the producer closes. The client_seq tag keeps two clients with the same client id apart.
 */
@ApplicationScoped
@Named("kafkaClientMetricsFactory")
public class KafkaClientMetricsFactory extends DefaultKafkaClientFactory {

    @Inject
    MeterRegistry registry;

    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Producer getProducer(Properties kafkaProps) {
        return new MeteredProducer<>(kafkaProps, registry, sequence.incrementAndGet());
    }

    static final class MeteredProducer<K, V> extends KafkaProducer<K, V> {

        private final KafkaClientMetrics metrics;

        MeteredProducer(Properties properties, MeterRegistry registry, int sequence) {
            super(properties);
            metrics = new KafkaClientMetrics(this, Tags.of("client_seq", String.valueOf(sequence)));
            metrics.bindTo(registry);
        }

        @Override
        public void close() {
            metrics.close();
            super.close();
        }

        @Override
        public void close(Duration timeout) {
            metrics.close();
            super.close(timeout);
        }
    }
}
