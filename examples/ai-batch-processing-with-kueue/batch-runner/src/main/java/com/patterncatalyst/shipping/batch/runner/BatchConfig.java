package com.patterncatalyst.shipping.batch.runner;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Where the Jobs go and the demo switches. The switches come from the environment of the runner. */
@ConfigMapping(prefix = "batch")
public interface BatchConfig {

    String namespace();

    String workerImage();

    String queueName();

    /** QUEUE=off leaves the queue label off every Job. */
    @WithDefault("on")
    String queue();

    /** PRIORITY=off leaves the priority label off every Job. */
    @WithDefault("on")
    String priority();

    /** FAIL_POLICY=off omits the pod failure policy of the reconcile Job. */
    @WithDefault("on")
    String failPolicy();

    /** REPLAY_GUARD=off is handed to the workers. */
    @WithDefault("on")
    String replayGuard();

    String faultsConfigMap();

    default boolean on(String value) {
        return !"off".equalsIgnoreCase(value);
    }
}
