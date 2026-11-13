package com.patterncatalyst.shipping.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** The rule lives in the registry. Each test uses its own artifact so that the versions do not mix. */
@QuarkusTest
class RegistryRuleTest {

    @Test
    void aTransitiveRuleAcceptsV1AndV2AndRefusesV3() throws Exception {
        Registry.globalRule("BACKWARD_TRANSITIVE");
        var serializer = Registry.serializer();
        serializer.serialize("rule-transitive", Registry.record(Registry.v1(), null));
        serializer.serialize("rule-transitive", Registry.v2Record("EXPRESS"));
        assertEquals(2, Registry.versionCount("rule-transitive-value"));

        assertThrows(RuntimeException.class,
                () -> serializer.serialize("rule-transitive", Registry.record(Registry.v3(), "EXPRESS")));

        var refusal = Registry.addVersion("rule-transitive-value", Registry.v3());
        assertEquals(400, refusal.status());
        assertTrue(refusal.body().contains("Incompatible artifact"));
        assertTrue(refusal.body().contains("shippingPriority"));
        assertEquals(2, Registry.versionCount("rule-transitive-value"));
    }

    @Test
    void aBackwardRuleAcceptsV3() throws Exception {
        Registry.globalRule("BACKWARD_TRANSITIVE");
        var serializer = Registry.serializer();
        serializer.serialize("rule-backward", Registry.record(Registry.v1(), null));
        assertEquals(204, Registry.artifactRule("rule-backward-value", "BACKWARD").status());
        serializer.serialize("rule-backward", Registry.v2Record("EXPRESS"));
        serializer.serialize("rule-backward", Registry.record(Registry.v3(), "EXPRESS"));
        assertEquals(3, Registry.versionCount("rule-backward-value"));
    }

    @Test
    void aFirstVersionIsAcceptedWhateverItContains() throws Exception {
        Registry.globalRule("BACKWARD_TRANSITIVE");
        Registry.serializer().serialize("rule-first", Registry.record(Registry.v3(), "EXPRESS"));
        assertEquals(1, Registry.versionCount("rule-first-value"));
    }
}
