package com.patterncatalyst.shipping.replay;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;

/** Shared by the projection tests: start from an empty read model and apply every batch by hand. */
abstract class ProjectionBase {

    @Inject
    AgroalDataSource ds;

    @Inject
    ProjectionBatch batch;

    @Inject
    CarrierDailyStore store;

    @Inject
    ReplayReport replay;

    /** Rewinds the projection and applies batches of {@code limit} until nothing is left below the watermark. */
    void rebuild(int limit) {
        batch.reset();
        drain(limit);
    }

    void drain(int limit) {
        while (batch.run(limit) > 0) {
            // keep going
        }
    }
}
