package org.misqzy.flectonegrowth.common.platform;

/** A handle for a task previously scheduled through {@link Scheduler}. */
public interface ScheduledTask {
    void cancel();
}
