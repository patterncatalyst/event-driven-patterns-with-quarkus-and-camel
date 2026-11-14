package com.patterncatalyst.shipping.join;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        Thread.sleep(1500);
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM shipping.readiness")) {
            result.next();
            assertEquals(0, result.getLong(1), "the group O-M2 is open and is not in the table");
        }
    }
}
