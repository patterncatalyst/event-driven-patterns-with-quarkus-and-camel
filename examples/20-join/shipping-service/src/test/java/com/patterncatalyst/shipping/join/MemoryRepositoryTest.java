package com.patterncatalyst.shipping.join;

import static org.awaitility.Awaitility.await;

import java.time.Duration;

import javax.sql.DataSource;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** With the in-memory repository the open group is in the JVM and nowhere else, so a restart cannot find it. */
@QuarkusTest
@QuarkusTestResource(StateDir.class)
@TestProfile(MemoryRepositoryProfile.class)
class MemoryRepositoryTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    DataSource dataSource;

    @Test
    void theOpenGroupLeavesNoRow() throws Exception {
        Kafka.reserve(bootstrap, "O-M1", "09:00");
        Kafka.pay(bootstrap, "O-M1", "09:01");
        Kafka.await(() -> {
            try {
                return !Kafka.forOrder(Kafka.readiness(bootstrap, Topics.SHIPPING_ORDER_READINESS_CAMEL), "O-M1").isEmpty();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Kafka.reserve(bootstrap, "O-M2", "09:02");
        // The open group O-M2 is held in the JVM, so the table stays empty, sampled over a further 1.5 s.
        await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(30)).until(() -> rows() == 0);
    }

    private long rows() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM shipping.readiness")) {
            result.next();
            return result.getLong(1);
        }
    }
}
