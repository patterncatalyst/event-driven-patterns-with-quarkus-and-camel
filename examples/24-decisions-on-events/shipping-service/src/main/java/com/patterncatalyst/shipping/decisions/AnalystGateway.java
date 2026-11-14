package com.patterncatalyst.shipping.decisions;

import dev.langchain4j.service.output.OutputParsingException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Calls the analyst and keeps its answer inside the closed set. An answer the schema cannot parse becomes UNKNOWN,
 * which the rules decide like any other reason and a counter records; a model that cannot be reached throws, so the
 * record is not consumed. The calls themselves are counted by Quarkus LangChain4j (langchain4j_aiservices_counted_total).
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
