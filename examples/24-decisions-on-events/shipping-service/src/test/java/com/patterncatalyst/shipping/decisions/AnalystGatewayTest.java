package com.patterncatalyst.shipping.decisions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.langchain4j.service.output.OutputParsingException;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class AnalystGatewayTest {

    @InjectMock
    DeliveryExceptionAnalyst analyst;

    @Inject
    AnalystGateway gateway;

    @Inject
    MeterRegistry meters;

    private double unparsed() {
        return meters.counter(AnalystGateway.CALLS, "outcome", "unparsed").count();
    }

    @Test
    void anAnswerInsideTheSchemaPassesThrough() {
        when(analyst.analyse(anyString())).thenReturn(new ExceptionAnalysis(Reason.LOST, "never seen again"));
        double before = unparsed();
        assertEquals(new ExceptionAnalysis(Reason.LOST, "never seen again"), gateway.analyse("n"));
        assertEquals(before, unparsed());
    }

    @Test
    void anAnswerTheSchemaCannotParseBecomesUnknownAndIsCounted() {
        when(analyst.analyse(anyString())).thenThrow(new OutputParsingException("not json", new IllegalArgumentException("no such reason")));
        double before = unparsed();
        assertEquals(Reason.UNKNOWN, gateway.analyse("n").reason());
        assertEquals(before + 1, unparsed());
    }

    @Test
    void aModelThatCannotBeReachedThrowsInsteadOfAnswering() {
        when(analyst.analyse(anyString())).thenThrow(new IllegalStateException("connection refused"));
        double before = unparsed();
        assertThrows(IllegalStateException.class, () -> gateway.analyse("n"));
        assertEquals(before, unparsed());
    }
}
