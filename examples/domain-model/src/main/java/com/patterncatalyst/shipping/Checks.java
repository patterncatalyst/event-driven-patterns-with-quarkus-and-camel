package com.patterncatalyst.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Validation helpers shared by the records in this package. */
final class Checks {

    private Checks() {
    }

    static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /** Normalises an amount to two decimals, HALF_UP, and requires the result to be positive. */
    static BigDecimal money(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return scaled;
    }

    static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static int nonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
