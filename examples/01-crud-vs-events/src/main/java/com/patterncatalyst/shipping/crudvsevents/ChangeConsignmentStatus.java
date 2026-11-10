package com.patterncatalyst.shipping.crudvsevents;

import com.patterncatalyst.shipping.ConsignmentStatus;

/**
 * The command: move a Consignment to a new status. {@code expectedVersion} is the stream
 * version the caller last saw; null means "whatever is current".
 */
public record ChangeConsignmentStatus(String consignmentId, ConsignmentStatus to, Integer expectedVersion) {
}
