package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplication;

import java.util.Objects;

/**
 * An application that has been waiting on the company for a while, together
 * with how long it has been waiting.
 *
 * <p>The number of days is carried alongside the application rather than
 * recomputed by the caller, so every place that shows the wait shows the same
 * number, measured at the same moment.
 */
public record WaitingApplication(JobApplication application, long daysWaiting) {

    public WaitingApplication {
        Objects.requireNonNull(application, "Application is required");
        if (daysWaiting < 0) {
            throw new IllegalArgumentException("Days waiting must not be negative");
        }
    }

    @Override
    public String toString() {
        return "%s at %s — %d day%s with no answer"
                .formatted(application.position(), application.company(),
                        daysWaiting, daysWaiting == 1 ? "" : "s");
    }
}
