package com.patterncatalyst.shipping.aggregate;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * PaymentProcessed becomes a MarkPaid command. A refusal is a business outcome: the transaction has logged it, and
 * the record is acknowledged. A conflict that outlasts the retries propagates and fails the channel.
 */
@ApplicationScoped
public class PaymentConsumer {

    @Inject
    OrderCommands commands;

    @Incoming("payments")
    @Blocking
    public void on(PaymentProcessed processed) {
        var payment = processed.payment();
        try {
            commands.handle(new Command.MarkPaid(payment.orderId(), payment.id(), payment.amount()));
        } catch (CommandRefused refused) {
            // acknowledged; the refusal is already logged and counted
        }
    }
}
