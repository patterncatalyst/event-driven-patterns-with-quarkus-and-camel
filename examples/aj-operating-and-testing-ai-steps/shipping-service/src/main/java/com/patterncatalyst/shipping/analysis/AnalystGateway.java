package com.patterncatalyst.shipping.analysis;

import dev.langchain4j.service.output.OutputParsingException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Calls the analyst and keeps its answer inside the closed set, reused unchanged from chapter 24. An answer the
 * schema cannot parse becomes UNKNOWN, which a counter records; a model that cannot be reached throws, so the row
 * is never committed and the note is retried.
 */
@ApplicationScoped
public class AnalystGateway {

    static final String CALLS = "exception.model.calls";

    @Inject
    DeliveryExceptionAnalyst analyst;

    @Inject
    MeterRegistry meters;

    public ExceptionAnalysis analyse(String note) {
        try {
            ExceptionAnalysis answer = analyst.analyse(note);
            if (answer == null || answer.reason() == null) {
                return unparsed();
            }
            return answer;
        } catch (OutputParsingException e) {
            return unparsed();
        }
    }

    private ExceptionAnalysis unparsed() {
        meters.counter(CALLS, "outcome", "unparsed").increment();
        return new ExceptionAnalysis(Reason.UNKNOWN, "");
    }
}
