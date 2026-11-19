package com.patterncatalyst.shipping.analysis;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Demo code, not part of the pattern. In its default mode the replay re-derives every disposition from {@link
 * DispositionSnapshot} with zero model calls: the snapshot already holds the reason and attempt {@link
 * DispositionFunction} needs. {@code REPLAY_MODE=reanalyse} is the negative witness: it reads the original notes
 * from the delivery-exceptions topic and calls the model again for each one instead.
 */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class ReplayResource {

    public record ReplayedDisposition(String exceptionId, String orderId, Disposition disposition, Reason reason, int attempt) {
    }

    public record Replay(String mode, long millis, List<ReplayedDisposition> dispositions) {
    }

    @Inject
    AnalystGateway gateway;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "camel.component.kafka.brokers")
    String brokers;

    /** {@code recorded} (the default) reads the disposition snapshot; {@code reanalyse} asks the model again. */
    @ConfigProperty(name = "replay.mode", defaultValue = "recorded")
    String mode;

    @POST
    @Path("/replay")
    public Replay replay() {
        long start = System.nanoTime();
        List<ReplayedDisposition> dispositions = "reanalyse".equals(mode) ? reanalyseFromNotes() : fromSnapshot();
        return new Replay(mode, (System.nanoTime() - start) / 1_000_000, dispositions);
    }

    private List<ReplayedDisposition> fromSnapshot() {
        return DispositionSnapshot.<DispositionSnapshot>listAll().stream()
                .map(s -> new ReplayedDisposition(s.exceptionId, s.orderId, s.disposition, s.reason, s.attempt))
                .toList();
    }

    private List<ReplayedDisposition> reanalyseFromNotes() {
        return RecordedTopic.read(brokers, Topics.SHIPPING_DELIVERY_EXCEPTIONS, DeliveryExceptionReported.class, mapper).stream()
                .map(reported -> {
                    ExceptionAnalysis answer = gateway.analyse(reported.note());
                    Disposition disposition = DispositionFunction.decide(answer.reason(), reported.attempt());
                    return new ReplayedDisposition(reported.exceptionId(), reported.shipment().orderId(), disposition,
                            answer.reason(), reported.attempt());
                }).toList();
    }
}
