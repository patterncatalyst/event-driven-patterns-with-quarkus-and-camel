package com.patterncatalyst.shipping.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** The route with its Kafka ends replaced: no broker, no consumer group, one exchange sent in by hand. */
@QuarkusTest
@TestProfile(AdviceProfile.class)
class OrderPlacedAdviceTest extends CamelQuarkusTestSupport {

    @Inject
    NotificationLog log;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    void theRouteNotifiesOncePerPlacedOrderAndSkipsACancelledOne() throws Exception {
        AdviceWith.adviceWith(context, "notify-order", route -> {
            route.replaceFromWith("direct:in");
            route.weaveByToUri("kafka:" + OrderPlacedNotificationRoute.NOTIFIED + "*").replace().to("mock:out");
        });
        context.start();

        StubOrderService.STATUS.put("O-3692", OrderStatus.CANCELLED);
        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(1);
        template.sendBody("direct:in", Events.orderPlaced("E-3691", "O-3691"));
        template.sendBody("direct:in", Events.orderPlaced("E-3692", "O-3692"));
        out.assertIsSatisfied();

        assertEquals("O-3691", out.getExchanges().get(0).getIn().getHeader(KafkaConstants.KEY));
        assertEquals(1, log.count("O-3691"));
        assertEquals(0, log.count("O-3692"));
    }
}
