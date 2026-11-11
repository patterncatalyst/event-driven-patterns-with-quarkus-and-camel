package com.patterncatalyst.shipping.tuning;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Stands in for the work a real notification needs before it is sent, such as rendering a template.
 * {@code NOTIFY_WORK_MS} sets a fixed cost per record, so a run can show what per-record work does
 * to {@code max.poll.interval.ms} without depending on a real renderer.
 */
@ApplicationScoped
@Named("notificationTemplate")
public class NotificationTemplate {

    @ConfigProperty(name = "notify.work-ms")
    long workMs;

    public void render() throws InterruptedException {
        if (workMs > 0) {
            Thread.sleep(workMs);
        }
    }
}
