package com.patterncatalyst.shipping.tuning;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.kafka.DefaultKafkaClientFactory;
import org.apache.kafka.clients.consumer.CloseOptions;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Camel creates its Kafka consumers itself, so Quarkus does not bind their client metrics. This
 * factory (camel.component.kafka.kafka-client-factory=#meteredFactory) hands Camel a consumer that
 * registers Micrometer's {@link KafkaClientMetrics} and removes them when it closes.
 *
 * <p>consumersCount threads share one client.id, and Micrometer drops the meters of a second client
 * with the same tags, so each consumer also gets a {@code client_seq} tag.
 */
@ApplicationScoped
@Named("meteredFactory")
public class KafkaClientMetricsFactory extends DefaultKafkaClientFactory {

    @Inject
    MeterRegistry registry;

    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Consumer getConsumer(Properties kafkaProps) {
        return new MeteredConsumer<>(kafkaProps, registry, sequence.incrementAndGet());
    }

    static final class MeteredConsumer<K, V> extends KafkaConsumer<K, V> {

        private final KafkaClientMetrics metrics;

        MeteredConsumer(Properties properties, MeterRegistry registry, int seq) {
            super(properties);
            metrics = new KafkaClientMetrics(this, Tags.of("client_seq", String.valueOf(seq)));
            metrics.bindTo(registry);
        }

        // Camel closes through whichever overload its shutdown path uses, so all three unbind.
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

        @Override
        public void close(CloseOptions options) {
            metrics.close();
            super.close(options);
        }
    }
}
