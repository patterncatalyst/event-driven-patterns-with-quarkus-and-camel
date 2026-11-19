package com.patterncatalyst.shipping.analysis;

import java.time.Instant;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * The transactional half of the backfill: one exception at a time, so a model failure on one note does not roll
 * back the others. {@link BackfillResource} decides, per exception, whether {@link #reanalyseOne} (a model call) or
 * {@link #rebuildSnapshot} alone (no call) applies.
 */
@ApplicationScoped
public class BackfillService {

    @Inject
    AnalystGateway gateway;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id")
    String model;

    @ConfigProperty(name = "analyst.model-digest")
    String modelDigest;

    public List<AnalysisRecord> latestAnalyses() {
        return AnalysisRecord.latestPerException();
    }

    /** Calls the model for the note and appends a new row tagged with the target prompt version, then rebuilds the
     * snapshot from it. The analyst has one prompt; the target version names what the new row is tagged with,
     * normally the analyst's current {@link DeliveryExceptionAnalyst#PROMPT_VERSION}. */
    @Transactional
    public DeliveryExceptionAnalysed reanalyseOne(DeliveryExceptionReported reported, String promptVersion) {
        ExceptionAnalysis answer = gateway.analyse(reported.note());
        var shipment = reported.shipment();
        DeliveryExceptionAnalysed analysed = new DeliveryExceptionAnalysed(reported.exceptionId(), shipment.orderId(), shipment.id(),
                shipment.carrier(), reported.attempt(), answer.reason(), answer.evidence(), model, modelDigest, promptVersion,
                reported.occurredAt(), null, null);
        AnalysisRecord.getEntityManager().merge(AnalysisRecord.from(analysed));
        rebuildSnapshot(analysed);
        return analysed;
    }

    @Transactional
    public void rebuildSnapshot(DeliveryExceptionAnalysed analysed) {
        var row = new DispositionSnapshot();
        row.exceptionId = analysed.exceptionId();
        row.orderId = analysed.orderId();
        row.disposition = DispositionFunction.decide(analysed.reason(), analysed.attempt());
        row.reason = analysed.reason();
        row.attempt = analysed.attempt();
        row.promptVersion = analysed.promptVersion();
        row.modelDigest = analysed.modelDigest();
        row.updatedAt = Instant.now();
        DispositionSnapshot.getEntityManager().merge(row);
    }
}
