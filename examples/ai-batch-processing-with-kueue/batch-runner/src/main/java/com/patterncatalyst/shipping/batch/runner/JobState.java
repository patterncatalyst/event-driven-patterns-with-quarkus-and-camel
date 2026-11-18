package com.patterncatalyst.shipping.batch.runner;

import io.fabric8.kubernetes.api.model.batch.v1.Job;

/** What the reconcile tick makes of a Job it read. */
public enum JobState {
    MISSING, LOST, SUSPENDED, ACTIVE, COMPLETE, FAILED;

    public static JobState of(Job job) {
        if (job == null) {
            return MISSING;
        }
        if (job.getStatus() != null && job.getStatus().getConditions() != null) {
            for (var c : job.getStatus().getConditions()) {
                if ("True".equals(c.getStatus())) {
                    if ("Complete".equals(c.getType())) {
                        return COMPLETE;
                    }
                    if ("Failed".equals(c.getType())) {
                        return FAILED;
                    }
                }
            }
        }
        return job.getSpec() != null && Boolean.TRUE.equals(job.getSpec().getSuspend()) ? SUSPENDED : ACTIVE;
    }
}
