package com.patterncatalyst.shipping.enrich;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Stands in for shipping-service on the test application's own port. */
@Path("/carriers/{carrier}")
public class StubShippingService {

    static final Map<String, CarrierReference> CARRIERS = new ConcurrentHashMap<>();
    static final AtomicBoolean DOWN = new AtomicBoolean();

    static void reset() {
        DOWN.set(false);
        CARRIERS.put("FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        CARRIERS.put("AIRCO", Samples.airco());
    }

    @GET
    public RestResponse<CarrierReference> get(@RestPath String carrier) {
        if (DOWN.get()) {
            return RestResponse.status(RestResponse.Status.SERVICE_UNAVAILABLE);
        }
        CarrierReference reference = CARRIERS.get(carrier);
        return reference == null ? RestResponse.notFound() : RestResponse.ok(reference);
    }
}
