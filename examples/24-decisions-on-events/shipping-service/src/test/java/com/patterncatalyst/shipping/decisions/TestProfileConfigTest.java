package com.patterncatalyst.shipping.decisions;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** The build needs no Ollama and pulls no model: Dev Services for Ollama stays off. */
@QuarkusTest
class TestProfileConfigTest {

    @ConfigProperty(name = "quarkus.langchain4j.ollama.devservices.enabled")
    boolean devServices;

    @Test
    void ollamaDevServicesIsOffInTheTestProfile() {
        assertFalse(devServices);
    }
}
