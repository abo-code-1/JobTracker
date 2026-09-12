package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.JobApplicationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the applications that have gone quiet.
 *
 * <p>The tracker records when every status change happened but never used those
 * timestamps for anything. The most useful thing it can say to a job seeker is
 * which companies have stopped answering, so those can be chased or written off
 * instead of being waited on indefinitely.
 *
 * <p>Only applications waiting on <em>the company</em> count. A draft is waiting
 * on the candidate, and a closed application is not waiting at all — reporting
 * either as stale would bury the ones that actually need a follow-up.
 */
public final class StaleApplicationFinder {

    /** The statuses in which the next move belongs to the company. */
    private static final Set<JobApplicationStatus> WAITING_ON_COMPANY =
            Set.of(JobApplicationStatus.APPLIED, JobApplicationStatus.INTERVIEWING);

    private final JobApplicationRepository repository;
    private final Clock clock;

    public StaleApplicationFinder(JobApplicationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    /**
     * The applications that have been waiting on a company for at least
     * {@code days} days, the longest wait first.
     *
     * @param days how long silence has to last before it counts, at least one
     * @throws IllegalArgumentException if {@code days} is less than one
     */
    public List<WaitingApplication> findStalerThan(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Days must be at least 1, was " + days);
        }
        Instant now = clock.instant();
        return repository.findAll().stream()
                .filter(StaleApplicationFinder::isWaitingOnCompany)
                .map(application -> new WaitingApplication(application, daysWaiting(application, now)))
                .filter(waiting -> waiting.daysWaiting() >= days)
                .sorted(Comparator.comparingLong(WaitingApplication::daysWaiting).reversed())
                .toList();
    }

    /** How long this application has been sitting at its current status. */
    public long daysWaiting(JobApplication application) {
        Objects.requireNonNull(application, "Application is required");
        return daysWaiting(application, clock.instant());
    }

    public boolean isStale(JobApplication application, int days) {
        Objects.requireNonNull(application, "Application is required");
        return isWaitingOnCompany(application) && daysWaiting(application) >= days;
    }

    private static boolean isWaitingOnCompany(JobApplication application) {
        return WAITING_ON_COMPANY.contains(application.status());
    }

    /**
     * Measured from the last status change, or from registration when the
     * application has not moved in this run — an application loaded from storage
     * arrives without its history.
     */
    private static long daysWaiting(JobApplication application, Instant now) {
        Instant since = application.lastChangedAt().orElseGet(application::createdAt);
        long days = Duration.between(since, now).toDays();
        return Math.max(days, 0);
    }
}
