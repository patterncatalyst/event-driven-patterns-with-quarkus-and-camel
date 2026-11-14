package com.patterncatalyst.shipping.join;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Event times for the tests. They are on yesterday's date, from the clock: the broker refuses a record timestamp more
 * than an hour ahead, and Kafka Streams forwards the event time of its output.
 */
final class Times {

    private static final LocalDate YESTERDAY = LocalDate.now(ZoneOffset.UTC).minusDays(1);

    private Times() {
    }

    /** Yesterday at the given UTC time, for example at("09:30"). */
    static Instant at(String time) {
        return YESTERDAY.atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
    }

    /** The mapper the service uses: Java time as ISO-8601 text. */
    static ObjectMapper mapper() {
        return JsonMapper.builder().findAndAddModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }
}
