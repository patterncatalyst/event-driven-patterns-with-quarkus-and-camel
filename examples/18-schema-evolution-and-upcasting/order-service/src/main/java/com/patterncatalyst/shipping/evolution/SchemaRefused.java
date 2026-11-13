package com.patterncatalyst.shipping.evolution;

/** The registry refused the schema of an event. The command's transaction is rolled back with it. */
public class SchemaRefused extends RuntimeException {

    public SchemaRefused(String detail, Throwable cause) {
        super(detail, cause);
    }
}
