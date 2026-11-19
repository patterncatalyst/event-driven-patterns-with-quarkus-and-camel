package com.patterncatalyst.shipping.analysis;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stage one, reused unchanged from chapter 24: free text in, one value of a closed set out. The prompt is
 * versioned; a change to the text changes {@link #PROMPT_VERSION}, and every committed row carries the version it
 * was made with, which is half of {@link MemoizedAnalysisService}'s idempotency key. Application scoped: the
 * default request scope is not active on a Kafka consumer thread.
 */
@RegisterAiService
@ApplicationScoped
public interface DeliveryExceptionAnalyst {

    String PROMPT_VERSION = "v1";

    @SystemMessage("""
            You classify a carrier's free-text delivery exception note into exactly one reason.
            Reasons: DAMAGED, LOST, RECIPIENT_ABSENT, ADDRESS_PROBLEM, REFUSED, DELAYED, UNKNOWN (not about a delivery exception, or unsure).
            Answer with a JSON object with the fields reason and evidence (the short phrase from the note that supports the reason).
            Example note: "Door locked, neighbour not home, left card." -> RECIPIENT_ABSENT, "neighbour not home".
            """)
    @UserMessage("Note: {note}")
    ExceptionAnalysis analyse(@V("note") String note);
}
