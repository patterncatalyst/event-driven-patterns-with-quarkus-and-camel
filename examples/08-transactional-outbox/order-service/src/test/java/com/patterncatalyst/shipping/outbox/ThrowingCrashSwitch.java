package com.patterncatalyst.shipping.outbox;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/** Tests cannot halt their own JVM: the "crash" is an exception thrown at the same point. */
@Alternative
@Priority(1)
@ApplicationScoped
public class ThrowingCrashSwitch extends CrashSwitch {

    @Override
    protected void crash() {
        throw new IllegalStateException("crash after commit");
    }
}
