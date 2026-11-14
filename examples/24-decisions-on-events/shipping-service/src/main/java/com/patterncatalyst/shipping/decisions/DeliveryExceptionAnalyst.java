package com.patterncatalyst.shipping.decisions;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Stage one: free text in, one value of a closed set out. The prompt is versioned; a change to the text changes
 * {@link #PROMPT_VERSION}, and every recorded analysis carries the version it was made with.
 */
@RegisterAiService
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
