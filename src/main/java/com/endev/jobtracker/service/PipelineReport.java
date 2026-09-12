package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplicationStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * A snapshot of the whole pipeline: how many applications sit at each status,
 * and the rates a job seeker actually cares about.
 *
 * <p>Rates are measured against <em>submitted</em> applications, never against
 * drafts: something never sent cannot have earned a reply, and counting drafts
 * would quietly drag every rate down.
 */
public record PipelineReport(int total, Map<JobApplicationStatus, Long> countsByStatus) {

    public PipelineReport {
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

    /** Applications that actually reached a company, that is everything but drafts. */
    public long submitted() {
        return total - countOf(JobApplicationStatus.DRAFT);
    }

    /** Applications still moving: not accepted, rejected or withdrawn. */
    public long open() {
        return countOf(JobApplicationStatus.DRAFT)
                + countOf(JobApplicationStatus.APPLIED)
                + countOf(JobApplicationStatus.INTERVIEWING)
                + countOf(JobApplicationStatus.OFFER);
    }

    /** Share of submitted applications that produced an offer, from 0.0 to 1.0. */
    public double offerRate() {
        return rateOf(countOf(JobApplicationStatus.OFFER) + countOf(JobApplicationStatus.ACCEPTED));
    }

    /** Share of submitted applications the company turned down, from 0.0 to 1.0. */
    public double rejectionRate() {
        return rateOf(countOf(JobApplicationStatus.REJECTED));
    }

    private double rateOf(long amount) {
        long submitted = submitted();
        return submitted == 0 ? 0.0 : (double) amount / submitted;
    }
}
