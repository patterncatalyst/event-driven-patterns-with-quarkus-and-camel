package com.patterncatalyst.shipping.analysis;

/**
 * A pure function from a recorded reason and attempt to a disposition: deterministic plumbing for the backfill and
 * replay demo in this appendix, not the subject of it. The decision pattern itself (rules fired on a recorded
 * analysis) is chapter 24's; this function exists only so the snapshot and the replay have something to recompute.
 */
public final class DispositionFunction {

    private static final int ATTEMPT_LIMIT = 3;

    private DispositionFunction() {
    }

    public static Disposition decide(Reason reason, int attempt) {
        return switch (reason) {
            case DAMAGED, LOST -> attempt == 1 ? Disposition.RESHIP : Disposition.REFUND;
            case RECIPIENT_ABSENT, DELAYED -> attempt < ATTEMPT_LIMIT ? Disposition.RESHIP : Disposition.REFUND;
            case ADDRESS_PROBLEM, REFUSED, UNKNOWN -> Disposition.REFUND;
        };
    }
}
