package com.patterncatalyst.shipping.analysis;

import java.util.concurrent.atomic.AtomicInteger;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A CDI mock of the AI service: Quarkus registers a class annotated {@code @Mock} as the bean that answers for its
 * type during tests, ahead of the real LangChain4j-generated implementation, so mvn verify never reaches Ollama.
 * Counts calls instead of asking Mockito to.
 */
@Mock
@ApplicationScoped
public class MockAnalyst implements DeliveryExceptionAnalyst {

    private final AtomicInteger calls = new AtomicInteger();

    private volatile ExceptionAnalysis next = new ExceptionAnalysis(Reason.UNKNOWN, "");

    public void answer(ExceptionAnalysis answer) {
        this.next = answer;
    }

    public int callCount() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
        next = new ExceptionAnalysis(Reason.UNKNOWN, "");
    }

    @Override
    public ExceptionAnalysis analyse(String note) {
        calls.incrementAndGet();
        return next;
    }
}
