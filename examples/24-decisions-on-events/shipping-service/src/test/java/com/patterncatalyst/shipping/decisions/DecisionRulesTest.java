package com.patterncatalyst.shipping.decisions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The rule unit alone: no application, no broker, no model. */
class DecisionRulesTest {

    private final DecisionService service = new DecisionService();

    private Verdict fire(Reason reason, int attempt, int limit) {
        return service.fire(new Analysis("E-1", reason.name(), attempt), limit);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "DAMAGED|1|RESHIP|Damaged or lost, first attempt",
            "LOST|2|REFUND|Damaged or lost, later attempt",
            "RECIPIENT_ABSENT|2|RESHIP|Recipient absent or delayed, under the attempt limit",
            "DELAYED|3|REFUND|Recipient absent or delayed, at the attempt limit",
            "ADDRESS_PROBLEM|1|REFUND|Address problem or refused",
            "UNKNOWN|1|REFUND|Unknown reason"})
    void eachRuleFiresOnItsRowAndRecordsItsName(Reason reason, int attempt, String decision, String rule) {
        Verdict verdict = fire(reason, attempt, 3);
        assertEquals(decision, verdict.decision());
        assertEquals(rule, verdict.rule());
    }

    @Test
    void exactlyOneRuleDecidesEveryReasonAndAttempt() {
        // The table is written out here, apart from the rules, so a rule that overlaps or goes missing shows up.
        Map<Reason, String[]> expected = Map.of(
                Reason.DAMAGED, new String[] {"RESHIP", "REFUND", "REFUND"},
                Reason.LOST, new String[] {"RESHIP", "REFUND", "REFUND"},
                Reason.RECIPIENT_ABSENT, new String[] {"RESHIP", "RESHIP", "REFUND"},
                Reason.DELAYED, new String[] {"RESHIP", "RESHIP", "REFUND"},
                Reason.ADDRESS_PROBLEM, new String[] {"REFUND", "REFUND", "REFUND"},
                Reason.REFUSED, new String[] {"REFUND", "REFUND", "REFUND"},
                Reason.UNKNOWN, new String[] {"REFUND", "REFUND", "REFUND"});
        int cases = 0;
        for (Reason reason : Reason.values()) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                assertEquals(expected.get(reason)[attempt - 1], fire(reason, attempt, 3).decision(), reason + " attempt " + attempt);
                cases++;
            }
        }
        assertEquals(21, cases);
    }

    @Test
    void theAttemptLimitIsTheOneParameterThatMovesADecision() {
        assertEquals("RESHIP", fire(Reason.RECIPIENT_ABSENT, 2, 3).decision());
        assertEquals("REFUND", fire(Reason.RECIPIENT_ABSENT, 2, 2).decision());
        assertEquals("REFUND", fire(Reason.DAMAGED, 2, 99).decision());
    }

    @Test
    void theSameAnalysisAlwaysGivesTheSameDecisionRecord() {
        var analysed = Samples.analysed("E-fixed", Reason.DELAYED, 2);
        var first = service.decide(analysed, 3);
        for (int i = 0; i < 50; i++) {
            assertEquals(first, service.decide(analysed, 3));
        }
        assertEquals(analysed.occurredAt(), first.occurredAt());
        assertEquals(DecisionService.RULES_VERSION, first.rulesVersion());
        assertNotEquals(first, service.decide(analysed, 2));
    }
}
