package com.patterncatalyst.shipping;

import java.math.BigDecimal;
import java.util.List;
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

    static BigDecimal nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    static BigDecimal positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static <T> List<T> list(List<T> value, String name) {
        Objects.requireNonNull(value, name);
        return List.copyOf(value);
    }
}
