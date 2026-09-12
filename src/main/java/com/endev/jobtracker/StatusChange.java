package com.endev.jobtracker;

import java.time.Instant;
import java.util.Objects;

/**
 * One recorded move of an application from one status to another.
 *
 * <p>These entries are what let the tracker answer "when did this actually
 * happen?" — how long an application sat waiting for a reply, or when a
 * company finally said no.
 */
public record StatusChange(JobApplicationStatus from, JobApplicationStatus to, Instant at) {

    public StatusChange {
        Objects.requireNonNull(from, "Previous status is required");
        Objects.requireNonNull(to, "New status is required");
        Objects.requireNonNull(at, "Timestamp is required");
    }

    @Override
    public String toString() {
        return "%s -> %s at %s".formatted(from, to, at);
    }
}
