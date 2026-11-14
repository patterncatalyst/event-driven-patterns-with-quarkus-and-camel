package com.patterncatalyst.shipping.decisions;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Stage one end to end: a reported note becomes a recorded analysis. */
@ApplicationScoped
public class AnalysisService {

    @Inject
    AnalystGateway gateway;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id")
    String model;

    @ConfigProperty(name = "analyst.model-digest")
    String modelDigest;

    public DeliveryExceptionAnalysed analyse(DeliveryExceptionReported reported) {
        ExceptionAnalysis answer = gateway.analyse(reported.note());
        var shipment = reported.shipment();
        return new DeliveryExceptionAnalysed(reported.exceptionId(), shipment.orderId(), shipment.id(), shipment.carrier(),
                reported.attempt(), answer.reason(), answer.evidence(), model, modelDigest,
                DeliveryExceptionAnalyst.PROMPT_VERSION, reported.occurredAt());
    }
}
