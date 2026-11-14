package com.patterncatalyst.shipping.decisions;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Demo code, not part of the pattern: replays the recorded decisions and reports the model-call count. The replay
 * publishes nothing.
 */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

    public record Replay(String mode, long millis, List<DeliveryExceptionDecided> decisions) {
    }

    public record ModelCalls(long calls, long unparsed) {
    }

    @Inject
    DecisionService decisions;

    @Inject
    AnalysisService analysis;

    @Inject
    ObjectMapper mapper;

    @Inject
    AnalystGateway gateway;

    @ConfigProperty(name = "camel.component.kafka.brokers")
    String brokers;

    /** {@code recorded} (the default) decides each recorded analysis again; {@code reanalyse} asks the model again. */
    @ConfigProperty(name = "replay.mode", defaultValue = "recorded")
    String mode;

    /** Decides every recorded analysis again, from the first record. {@code attemptLimit} shows a rule change. */
    @POST
    @Path("/replay")
    public Replay replay(@QueryParam("attemptLimit") Integer attemptLimit) {
        long start = System.nanoTime();
        List<DeliveryExceptionAnalysed> analyses = "reanalyse".equals(mode)
                ? RecordedTopic.read(brokers, Topics.SHIPPING_DELIVERY_EXCEPTIONS, DeliveryExceptionReported.class, mapper)
                        .stream().map(analysis::analyse).toList()
                : RecordedTopic.read(brokers, Topics.SHIPPING_EXCEPTION_ANALYSES, DeliveryExceptionAnalysed.class, mapper);
        List<DeliveryExceptionDecided> result = analyses.stream()
                .map(a -> attemptLimit == null ? decisions.decide(a) : decisions.decide(a, attemptLimit)).toList();
        return new Replay(mode, (System.nanoTime() - start) / 1_000_000, result);
    }

    /** The model requests answered since the service started, which equal the requests Ollama served, and the unparsed ones. */
    @GET
    @Path("/model-calls")
    public ModelCalls modelCalls() {
        return new ModelCalls(gateway.answered(), gateway.count("unparsed"));
    }
}
