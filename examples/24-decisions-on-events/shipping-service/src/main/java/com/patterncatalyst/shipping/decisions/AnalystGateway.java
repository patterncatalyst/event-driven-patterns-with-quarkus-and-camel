package com.patterncatalyst.shipping.decisions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import dev.langchain4j.service.output.OutputParsingException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Calls the analyst and keeps its answer inside the closed set. An answer the schema cannot parse becomes UNKNOWN,
 * which the rules decide like any other reason; a model that cannot be reached throws, so the record is not consumed.
 */
@ApplicationScoped
public class AnalystGateway {

    static final String CALLS = "exception.model.calls";

    @Inject
    DeliveryExceptionAnalyst analyst;

    @Inject
    MeterRegistry meters;

    // The OpenTelemetry-backed registry exports counters but cannot read them back, so the gateway keeps its own tally
    // for the demo endpoint and the tests, next to the exported counter.
    private final Map<String, LongAdder> outcomes = new ConcurrentHashMap<>();

    public ExceptionAnalysis analyse(String note) {
        try {
            ExceptionAnalysis answer = analyst.analyse(note);
            if (answer == null || answer.reason() == null) {
                return unparsed();
            }
            record("ok");
            return answer;
        } catch (OutputParsingException e) {
            return unparsed();
        } catch (RuntimeException e) {
            record("error");
            throw e;
        }
    }

    /** Requests the model answered, parsed or not: the number Ollama's request log shows. */
    public long answered() {
        return count("ok") + count("unparsed");
    }

    public long count(String outcome) {
        var adder = outcomes.get(outcome);
        return adder == null ? 0 : adder.sum();
    }

    private void record(String outcome) {
        outcomes.computeIfAbsent(outcome, o -> new LongAdder()).increment();
        meters.counter(CALLS, "outcome", outcome).increment();
    }

    private ExceptionAnalysis unparsed() {
        record("unparsed");
        return new ExceptionAnalysis(Reason.UNKNOWN, "");
    }
}
