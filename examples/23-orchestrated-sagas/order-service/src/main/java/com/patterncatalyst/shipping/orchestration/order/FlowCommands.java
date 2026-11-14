package com.patterncatalyst.shipping.orchestration.order;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The commands a workflow sends, one emitter per command topic. Each record is keyed by order id and names its
 * workflow instance in a header, which the participants copy onto their reply as a CloudEvent attribute.
 */
@ApplicationScoped
public class FlowCommands {

    @Inject
    @Channel("inventory-commands")
    Emitter<String> inventory;

    @Inject
    @Channel("payment-commands")
    Emitter<String> payment;

    @Inject
    ObjectMapper json;

    public void reserve(String instanceId, ReserveStock command) {
        send(inventory, "ReserveStock", instanceId, command.orderId(), command);
    }

    public void release(String instanceId, ReleaseStock command) {
        send(inventory, "ReleaseStock", instanceId, command.orderId(), command);
    }

    public void take(String instanceId, TakePayment command) {
        send(payment, "TakePayment", instanceId, command.orderId(), command);
    }

    public void refund(String instanceId, RefundPayment command) {
        send(payment, "RefundPayment", instanceId, command.orderId(), command);
    }

    private void send(Emitter<String> emitter, String type, String instanceId, String orderId, Object command) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("type", type.getBytes(StandardCharsets.UTF_8));
        headers.add("flowinstanceid", instanceId.getBytes(StandardCharsets.UTF_8));
        headers.add("orderId", orderId.getBytes(StandardCharsets.UTF_8));
        try {
            emitter.send(Message.of(json.writeValueAsString(command)).addMetadata(
                    OutgoingKafkaRecordMetadata.<String>builder().withKey(orderId).withHeaders(headers).build()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
