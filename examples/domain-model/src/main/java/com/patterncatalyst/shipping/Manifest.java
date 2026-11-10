package com.patterncatalyst.shipping;

import java.util.List;

/** The consignments loaded on one sailing. */
public record Manifest(String id, String sailingId, List<String> consignmentIds) {

    public Manifest {
        Checks.text(id, "id");
        Checks.text(sailingId, "sailingId");
        consignmentIds = Checks.list(consignmentIds, "consignmentIds");
        consignmentIds.forEach(c -> Checks.text(c, "consignmentIds element"));
    }
}
