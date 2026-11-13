package com.patterncatalyst.shipping.replay;

import java.time.LocalDate;

/** The key of a projection row, and the grouping key of a replay. */
public record CarrierDay(String carrier, LocalDate day) implements Comparable<CarrierDay> {

    @Override
    public int compareTo(CarrierDay other) {
        int byCarrier = carrier.compareTo(other.carrier);
        return byCarrier != 0 ? byCarrier : day.compareTo(other.day);
    }
}
