package com.patterncatalyst.shipping;

import java.math.BigDecimal;

/** The goods and value declared to customs for a consignment. */
public record CustomsDeclaration(String id, String consignmentId, String destinationCountry,
                                 BigDecimal declaredValue, String currency) {

    public CustomsDeclaration {
        Checks.text(id, "id");
        Checks.text(consignmentId, "consignmentId");
        Checks.text(destinationCountry, "destinationCountry");
        Checks.nonNegative(declaredValue, "declaredValue");
        Checks.text(currency, "currency");
    }
}
