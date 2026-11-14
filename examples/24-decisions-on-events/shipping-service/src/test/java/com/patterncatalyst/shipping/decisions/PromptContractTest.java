package com.patterncatalyst.shipping.decisions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** The prompt and the answer's shape are fixed text: a change to either is a new prompt version. */
class PromptContractTest {

    private static String golden() throws IOException {
        try (var in = PromptContractTest.class.getResourceAsStream("/golden/prompt-" + DeliveryExceptionAnalyst.PROMPT_VERSION + ".txt")) {
            assertTrue(in != null, "no golden prompt for version " + DeliveryExceptionAnalyst.PROMPT_VERSION
                    + ": a changed prompt needs a new PROMPT_VERSION and a new golden file");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void thePromptTextMatchesTheGoldenFileOfItsVersion() throws Exception {
        Method method = DeliveryExceptionAnalyst.class.getMethod("analyse", String.class);
        String text = String.join("\n", method.getAnnotation(SystemMessage.class).value()) + "\n---\n"
                + String.join("\n", method.getAnnotation(UserMessage.class).value()) + "\n";
        assertEquals(golden(), text);
    }

    @Test
    void thePromptNamesExactlyTheReasonsOfTheEnum() throws Exception {
        String reasons = Arrays.stream(Reason.values()).map(Reason::name).collect(Collectors.joining(", "));
        assertTrue(golden().contains("Reasons: " + reasons + " (not about"), "the prompt lists " + reasons);
    }

    @Test
    void everyComponentOfTheAnswerIsRequired() {
        for (var component : ExceptionAnalysis.class.getRecordComponents()) {
            var property = component.getAccessor().getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
            assertTrue(property != null && property.required(), component.getName() + " must be required in the schema");
        }
    }

    @Test
    void theSchemaSentToTheModelHasTheEnumAndBothFieldsRequired() {
        var schema = dev.langchain4j.service.output.JsonSchemas.jsonSchemaFrom(ExceptionAnalysis.class).orElseThrow().toString();
        for (Reason reason : Reason.values()) {
            assertTrue(schema.contains(reason.name()), reason + " is in the schema");
        }
        assertTrue(schema.contains("reason") && schema.contains("evidence"));
    }
}
