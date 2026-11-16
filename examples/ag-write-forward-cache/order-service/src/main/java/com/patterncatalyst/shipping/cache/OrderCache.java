package com.patterncatalyst.shipping.cache;

import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/** The read side: one Redis hash per order, written only by {@link #apply} and read only by {@link #find}. */
@ApplicationScoped
@Named("orderCache")
public class OrderCache {

    private final RedisDataSource redis;
    private final ObjectMapper json;

    /** Demo code: {@code off} removes the version guard, so the last event applied wins. */
    @ConfigProperty(name = "cache.guard")
    String guard;

    OrderCache(RedisDataSource redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    static String key(String orderId) {
        return "order:" + orderId;
    }

    /** Stores the view unless the cache already holds this version or a newer one. Safe to repeat and to replay. */
    public void apply(OrderView view) throws JsonProcessingException {
        String key = key(view.orderId());
        Map<String, String> fields = Map.of("version", String.valueOf(view.version()), "json", json.writeValueAsString(view));
        if ("off".equals(guard)) {
            redis.hash(String.class).hset(key, fields);
            return;
        }
        // WATCH the key, read its version, and queue the write in MULTI/EXEC. If the key changes in between,
        // the transaction is discarded and the check runs again.
        boolean discarded;
        do {
            var result = redis.withTransaction(
                    reader -> reader.hash(String.class).hget(key, "version"),
                    (stored, tx) -> {
                        if (stored == null || Long.parseLong(stored) < view.version()) {
                            tx.hash(String.class).hset(key, fields);
                        }
                    },
                    key);
            discarded = result.discarded();
        } while (discarded);
    }

    public Optional<OrderView> find(String orderId) throws JsonProcessingException {
        String stored = redis.hash(String.class).hget(key(orderId), "json");
        return stored == null ? Optional.empty() : Optional.of(json.readValue(stored, OrderView.class));
    }
}
