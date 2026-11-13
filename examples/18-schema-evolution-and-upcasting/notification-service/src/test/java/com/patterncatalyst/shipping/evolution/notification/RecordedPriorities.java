package com.patterncatalyst.shipping.evolution.notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** What the service has recorded, for assertions: "priority/reader" or null. */
@ApplicationScoped
class RecordedPriorities {

    @Inject
    Recorder recorder;

    @Transactional
    String of(String orderId) {
        RecordedPriority row = recorder.findById(orderId);
        return row == null ? null : row.shippingPriority + "/" + row.via;
    }
}
