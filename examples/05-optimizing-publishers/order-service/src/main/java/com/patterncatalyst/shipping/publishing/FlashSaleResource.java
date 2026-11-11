package com.patterncatalyst.shipping.publishing;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

import org.apache.camel.ProducerTemplate;

import com.patterncatalyst.shipping.Order;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/** Load driver: places a burst of orders through one publish route with a fixed number of callers in flight. */
@Path("/flash-sale")
public class FlashSaleResource {

    public record Result(String profile, int orders, int concurrency, int failed, long elapsedMs,
            double ordersPerSecond, double p50Ms, double p99Ms) {
    }

    @Inject
    ProducerTemplate producer;

    @Inject
    MeterRegistry registry;

    @POST
    public Result run(@QueryParam("profile") String profile, @QueryParam("orders") int orders,
            @QueryParam("concurrency") int concurrency) {
        if (!List.of("unbatched", "default", "tuned").contains(profile) || orders < 1 || concurrency < 1) {
            throw new WebApplicationException("profile must be unbatched, default or tuned; orders and concurrency "
                    + "must be positive", Response.Status.BAD_REQUEST);
        }
        Timer latency = Timer.builder("edp.publish.latency")
                .tag("profile", profile)
                .publishPercentiles(0.5, 0.99)
                .register(registry);
        String run = UUID.randomUUID().toString().substring(0, 8);
        Semaphore inFlight = new Semaphore(concurrency);
        AtomicLongArray nanos = new AtomicLongArray(orders);
        AtomicInteger failed = new AtomicInteger();
        long start = System.nanoTime();
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < orders; i++) {
                int n = i;
                inFlight.acquireUninterruptibly();
                callers.submit(() -> {
                    try {
                        OrderPlaced event = OrderPlaced.of(new Order("O-" + run + "-" + n, "C-" + (n % 500),
                                "SKU-" + (n % 50), 1 + n % 5, BigDecimal.valueOf(10 + n % 90)));
                        long sent = System.nanoTime();
                        producer.sendBody("direct:publish-" + profile, event);
                        long took = System.nanoTime() - sent;
                        nanos.set(n, took);
                        latency.record(took, TimeUnit.NANOSECONDS);
                    } catch (RuntimeException e) {
                        failed.incrementAndGet();
                    } finally {
                        inFlight.release();
                    }
                });
            }
        }
        long elapsedNanos = System.nanoTime() - start;
        long[] sorted = new long[orders];
        for (int i = 0; i < orders; i++) {
            sorted[i] = nanos.get(i);
        }
        Arrays.sort(sorted);
        double seconds = elapsedNanos / 1e9;
        Log.infof("flash sale %s: %d orders on %s, %d callers, %d failed", run, orders, profile, concurrency,
                failed.get());
        return new Result(profile, orders, concurrency, failed.get(), elapsedNanos / 1_000_000,
                orders / seconds, percentile(sorted, 0.50), percentile(sorted, 0.99));
    }

    private static double percentile(long[] sorted, double p) {
        return sorted[(int) Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1)] / 1e6;
    }
}
