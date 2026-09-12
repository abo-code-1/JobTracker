package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplicationStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * What happened at one company: how its applications are spread across the
 * statuses, and how quickly it tends to answer.
 *
 * <p>The average reply time is an {@link OptionalDouble} rather than a number
 * defaulting to zero. A company that has never replied has no average, and
 * reporting zero would rank the silent companies as the fastest ones.
 */
public record CompanyStats(String company,
                           int total,
                           Map<JobApplicationStatus, Long> countsByStatus,
                           OptionalDouble averageDaysToFirstReply,
                           long repliesObserved) {

    public CompanyStats {
        Objects.requireNonNull(company, "Company is required");
        Objects.requireNonNull(averageDaysToFirstReply, "Average is required");
        if (total < 0) {
            throw new IllegalArgumentException("Total must not be negative");
        }
        Map<JobApplicationStatus, Long> complete = new EnumMap<>(JobApplicationStatus.class);
        for (JobApplicationStatus status : JobApplicationStatus.values()) {
            complete.put(status, countsByStatus == null ? 0L : countsByStatus.getOrDefault(status, 0L));
        }
        countsByStatus = Map.copyOf(complete);
    }

    public long countOf(JobApplicationStatus status) {
        return countsByStatus.getOrDefault(status, 0L);
    }

    /** Applications here that can still move. */
    public long open() {
        return countOf(JobApplicationStatus.DRAFT)
                + countOf(JobApplicationStatus.APPLIED)
                + countOf(JobApplicationStatus.INTERVIEWING)
                + countOf(JobApplicationStatus.OFFER);
    }

    public long offers() {
        return countOf(JobApplicationStatus.OFFER) + countOf(JobApplicationStatus.ACCEPTED);
    }

    /** Whether this company has ever answered, which is what makes the average meaningful. */
    public boolean hasReplied() {
        return repliesObserved > 0;
    }
}
