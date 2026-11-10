package com.patterncatalyst.shipping.crudvsevents;

import com.patterncatalyst.shipping.ConsignmentStatus;

/** Request body for a status change; the Consignment id comes from the path. */
public record StatusRequest(ConsignmentStatus to, Integer expectedVersion) {
}
