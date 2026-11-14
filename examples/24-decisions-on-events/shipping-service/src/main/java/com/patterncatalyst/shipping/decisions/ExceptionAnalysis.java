package com.patterncatalyst.shipping.decisions;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.model.output.structured.Description;

/**
 * What the model returns. Quarkus LangChain4j sends this record's JSON schema to Ollama as the response format, and
 * the schema only forces both fields when each component is marked required.
 */
public record ExceptionAnalysis(
        @Description("The closed reason for the delivery exception") @JsonProperty(required = true) Reason reason,
        @Description("The short phrase copied from the note that supports the reason") @JsonProperty(required = true) String evidence) {
}
