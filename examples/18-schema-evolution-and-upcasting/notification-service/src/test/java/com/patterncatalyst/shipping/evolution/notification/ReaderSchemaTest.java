package com.patterncatalyst.shipping.evolution.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** The reader keeps its own copy of the contract; the copies must not drift apart. */
class ReaderSchemaTest {

    @Test
    void theReaderSchemaIsTheContractOrderServicePublishes() throws Exception {
        Path reader = Path.of("src/main/avro/order-placed.avsc");
        Path contract = Path.of("../order-service/src/main/avro/order-placed.avsc");
        assertEquals(Files.readString(contract), Files.readString(reader));
    }
}
