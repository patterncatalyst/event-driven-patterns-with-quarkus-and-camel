package com.patterncatalyst.shipping.enrich;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;

/**
 * Demo code: reads the local copy of one carrier through an interactive query, so a caller can wait until the store
 * shows a reference change before it sends the next shipment.
 */
@Path("/demo/carriers/{carrier}")
@Produces(MediaType.APPLICATION_JSON)
public class CarrierStoreResource {

    @Inject
    KafkaStreams streams;

    @GET
    public CarrierReference get(@PathParam("carrier") String carrier) {
        ReadOnlyKeyValueStore<String, CarrierReference> store;
        try {
            store = streams.store(StoreQueryParameters.fromNameAndType(
                    ShipmentEnrichmentTopology.CARRIER_STORE, QueryableStoreTypes.keyValueStore()));
        } catch (InvalidStateStoreException notReady) {
            throw new ServiceUnavailableException();
        }
        CarrierReference reference = store.get(carrier);
        if (reference == null) {
            throw new NotFoundException();
        }
        return reference;
    }
}
