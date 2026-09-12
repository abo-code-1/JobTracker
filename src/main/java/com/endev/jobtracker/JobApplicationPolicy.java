package com.endev.jobtracker;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.endev.jobtracker.JobApplicationStatus.ACCEPTED;
import static com.endev.jobtracker.JobApplicationStatus.APPLIED;
import static com.endev.jobtracker.JobApplicationStatus.DRAFT;
import static com.endev.jobtracker.JobApplicationStatus.INTERVIEWING;
import static com.endev.jobtracker.JobApplicationStatus.OFFER;
import static com.endev.jobtracker.JobApplicationStatus.REJECTED;
import static com.endev.jobtracker.JobApplicationStatus.WITHDRAWN;

/**
 * The rules that decide which status change a job application is allowed to make.
 *
 * <p>An application always moves forward through the funnel or drops out of it.
 * ACCEPTED, REJECTED and WITHDRAWN are final: once the process is closed it stays
 * closed, and a new attempt at the same company means a new application.
 */
public final class JobApplicationPolicy {

    private static final Map<JobApplicationStatus, Set<JobApplicationStatus>> ALLOWED =
            new EnumMap<>(JobApplicationStatus.class);

    static {
        ALLOWED.put(DRAFT, Set.of(APPLIED, WITHDRAWN));
        ALLOWED.put(APPLIED, Set.of(INTERVIEWING, REJECTED, WITHDRAWN));
        ALLOWED.put(INTERVIEWING, Set.of(OFFER, REJECTED, WITHDRAWN));
        ALLOWED.put(OFFER, Set.of(ACCEPTED, WITHDRAWN));
        ALLOWED.put(ACCEPTED, Set.of());
        ALLOWED.put(REJECTED, Set.of());
        ALLOWED.put(WITHDRAWN, Set.of());
    }

    /**
     * Applies a status change.
     *
     * @param from the current status of the application
     * @param to   the status the application should move to
     * @return {@code to}, when the change is allowed
     * @throws IllegalArgumentException if either status is {@code null}
     * @throws IllegalStateException    if the change is forbidden by the workflow
     */
    public JobApplicationStatus move(JobApplicationStatus from, JobApplicationStatus to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both the current and the target status are required");
        }
        if (!ALLOWED.get(from).contains(to)) {
            throw new IllegalStateException("Cannot move a job application from " + from + " to " + to);
        }
        return to;
    }
}
