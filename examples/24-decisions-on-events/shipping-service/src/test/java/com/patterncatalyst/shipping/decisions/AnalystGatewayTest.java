package com.patterncatalyst.shipping.decisions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.langchain4j.service.output.OutputParsingException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class AnalystGatewayTest {

    @InjectMock
    DeliveryExceptionAnalyst analyst;

    @Inject
    AnalystGateway gateway;

    private long count(String outcome) {
        return gateway.count(outcome);
    }

    @Test
    void anAnswerInsideTheSchemaPassesThroughAndIsCounted() {
        when(analyst.analyse(anyString())).thenReturn(new ExceptionAnalysis(Reason.LOST, "never seen again"));
        long before = count("ok");
        assertEquals(new ExceptionAnalysis(Reason.LOST, "never seen again"), gateway.analyse("n"));
        assertEquals(before + 1, count("ok"));
    }

    @Test
    void anAnswerTheSchemaCannotParseBecomesUnknownAndIsCounted() {
        when(analyst.analyse(anyString())).thenThrow(new OutputParsingException("not json", new IllegalArgumentException("no such reason")));
        long before = count("unparsed");
        assertEquals(Reason.UNKNOWN, gateway.analyse("n").reason());
        assertEquals(before + 1, count("unparsed"));
    }

    @Test
    void aModelThatCannotBeReachedThrowsInsteadOfAnswering() {
        when(analyst.analyse(anyString())).thenThrow(new IllegalStateException("connection refused"));
        long before = count("error");
        assertThrows(IllegalStateException.class, () -> gateway.analyse("n"));
        assertEquals(before + 1, count("error"));
    }
}
