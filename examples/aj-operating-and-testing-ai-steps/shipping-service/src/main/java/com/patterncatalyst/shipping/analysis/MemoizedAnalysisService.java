package com.patterncatalyst.shipping.analysis;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * The exactly-once effect around the model call. A committed {@link AnalysisRecord} at (exceptionId, promptVersion,
 * modelDigest) is the idempotency guard on its own: no separate inbox table is needed, because the row the model
 * call produces is exactly the row a second delivery would look up. The lookup and the write happen in the same
 * transaction, so a redelivered or duplicated note either finds nothing and calls the model once, or finds the row
 * another delivery already committed and returns it untouched.
 */
@ApplicationScoped
public class MemoizedAnalysisService {

    static final String LOOKUPS = "exception.analysis.lookups";

    @Inject
    AnalystGateway gateway;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id")
    String model;

    @ConfigProperty(name = "analyst.model-digest")
    String modelDigest;

    /** Demo code: false removes the keyed lookup, so a redelivered or duplicated note calls the model again
     * (the negative witness; set with ANALYSIS_DEDUP). */
    @ConfigProperty(name = "analysis.dedup")
    boolean dedupEnabled;

    @Transactional
    public DeliveryExceptionAnalysed analyseOnce(DeliveryExceptionReported reported) {
        String promptVersion = DeliveryExceptionAnalyst.PROMPT_VERSION;
        if (dedupEnabled) {
            AnalysisRecord existing = AnalysisRecord.findByKey(reported.exceptionId(), promptVersion, modelDigest);
            if (existing != null) {
                meters.counter(LOOKUPS, "outcome", "hit").increment();
                return existing.toEvent();
            }
        }
        meters.counter(LOOKUPS, "outcome", "miss").increment();
        ExceptionAnalysis answer = gateway.analyse(reported.note());
        var shipment = reported.shipment();
        DeliveryExceptionAnalysed fresh = new DeliveryExceptionAnalysed(reported.exceptionId(), shipment.orderId(), shipment.id(),
                shipment.carrier(), reported.attempt(), answer.reason(), answer.evidence(), model, modelDigest, promptVersion,
                reported.occurredAt(), null, null);
        AnalysisRecord.getEntityManager().merge(AnalysisRecord.from(fresh));
        return fresh;
    }
}
