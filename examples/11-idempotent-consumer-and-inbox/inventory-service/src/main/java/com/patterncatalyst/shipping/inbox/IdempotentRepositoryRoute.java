package com.patterncatalyst.shipping.inbox;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jpa.TransactionStrategy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.jpa.JpaMessageIdRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

/**
 * The same reservation through Camel's Idempotent Consumer EIP. The JPA repository joins the route's JTA
 * transaction, so its key row and the decrement commit or roll back together.
 */
@ApplicationScoped
public class IdempotentRepositoryRoute extends RouteBuilder {

    @Inject
    EntityManagerFactory entityManagerFactory;

    @Inject
    TransactionStrategy transactionStrategy;

    @ConfigProperty(name = "inventory.camel.transacted", defaultValue = "true")
    boolean transacted;

    @Override
    public void configure() {
        JpaMessageIdRepository repository = new JpaMessageIdRepository(entityManagerFactory, transactionStrategy,
                "inventory-reservations");

        RouteDefinition route = from("kafka:" + Topics.ORDERS_PLACED
                + "?groupId=inventory-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000")
                .routeId("idempotent-reservation")
                .autoStartup("{{inventory.camel.enabled}}");
        if (transacted) {
            route.transacted();
        }
        route.unmarshal().json(JsonLibrary.Jackson, OrderPlaced.class)
                .idempotentConsumer(header("id"), repository).removeOnFailure(false)
                    .bean(ReservationService.class, "reserve")
                .end();
    }
}
