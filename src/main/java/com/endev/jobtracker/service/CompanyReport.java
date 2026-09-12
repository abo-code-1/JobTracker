package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.StatusChange;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The pipeline broken down by company.
 *
 * <p>{@link PipelineReport} says how the search is going overall, which does not
 * help decide where to spend the next evening. This one says which companies
 * answer, which ones go quiet, and how long they take.
 *
 * <p>Companies are matched ignoring case, so "Kaspi Bank" and "kaspi bank" are
 * one company rather than two rows that each look half as active. The spelling
 * shown is the one first seen.
 */
public final class CompanyReport {

    private final List<CompanyStats> stats;

    private CompanyReport(List<CompanyStats> stats) {
        this.stats = List.copyOf(stats);
    }

    public static CompanyReport of(Collection<JobApplication> applications) {
        Objects.requireNonNull(applications, "Applications are required");

        Map<String, List<JobApplication>> grouped = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (JobApplication application : applications) {
            String key = application.company().toLowerCase();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(application);
            displayNames.putIfAbsent(key, application.company());
        }

        List<CompanyStats> stats = new ArrayList<>();
        grouped.forEach((key, group) -> stats.add(summarise(displayNames.get(key), group)));
        return new CompanyReport(stats);
    }

    /** One row per company, busiest first, then alphabetically. */
    public List<CompanyStats> byCompany() {
        return stats.stream()
                .sorted(Comparator.comparingInt(CompanyStats::total).reversed()
                        .thenComparing(CompanyStats::company, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<CompanyStats> forCompany(String company) {
        Objects.requireNonNull(company, "Company is required");
        return stats.stream()
                .filter(row -> row.company().equalsIgnoreCase(company.strip()))
                .findFirst();
    }

    public int companies() {
        return stats.size();
    }

    private static CompanyStats summarise(String company, List<JobApplication> group) {
        Map<JobApplicationStatus, Long> counts = new EnumMap<>(JobApplicationStatus.class);
        double totalDays = 0;
        long replies = 0;

        for (JobApplication application : group) {
            counts.merge(application.status(), 1L, Long::sum);
            OptionalDouble days = daysToFirstReply(application);
            if (days.isPresent()) {
                totalDays += days.getAsDouble();
                replies++;
            }
        }

        OptionalDouble average = replies == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of(totalDays / replies);
        return new CompanyStats(company, group.size(), counts, average, replies);
    }

    /**
     * How long the company took to answer, in days, or empty when it never did.
     *
     * <p>Withdrawing does not count as a reply: the candidate moved, not the
     * company, and treating it as an answer would flatter every company the
     * candidate gave up on.
     */
    private static OptionalDouble daysToFirstReply(JobApplication application) {
        Instant sentAt = null;
        for (StatusChange change : application.history()) {
            if (change.to() == JobApplicationStatus.APPLIED) {
                sentAt = change.at();
            } else if (change.from() == JobApplicationStatus.APPLIED
                    && change.to() != JobApplicationStatus.WITHDRAWN
                    && sentAt != null) {
                return OptionalDouble.of(daysBetween(sentAt, change.at()));
            }
        }
        return OptionalDouble.empty();
    }

    private static double daysBetween(Instant from, Instant to) {
        return Duration.between(from, to).toSeconds() / 86_400.0;
    }
}
