package com.patterncatalyst.shipping.ordering;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/** Builds the record key for an event with the configured strategy. */
@ApplicationScoped
@Named("partitionKeyChooser")
public class PartitionKeyChooser {

    @ConfigProperty(name = "shipping.partition-key", defaultValue = "order-id")
    PartitionKeyStrategy strategy;

    public String keyFor(ShipmentEvent event) {
        return strategy.keyFor(event);
    }
}
