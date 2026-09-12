package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationStatus;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A question to ask the tracker: which applications, in which order.
 *
 * <p>Once there are twenty applications, listing them all is a wall of text. The
 * useful questions are narrower — everything at one company, everything still
 * waiting for a reply, whatever was registered this month — and this class lets
 * them be expressed without the service growing a {@code findByXAndYSortedByZ}
 * method for every combination.
 *
 * <p>A query is immutable: each method returns a new query, so one can be built
 * up in steps and reused safely.
 *
 * <pre>
 * ApplicationQuery.all()
 *         .company("kaspi")
 *         .statuses(JobApplicationStatus.APPLIED, JobApplicationStatus.INTERVIEWING)
 *         .sortedBy(ApplicationQuery.Sort.NEWEST_FIRST);
 * </pre>
 */
public final class ApplicationQuery {

    /** The orders a result list can come back in. */
    public enum Sort {
        /** Registered earliest first — the order applications were added. */
        OLDEST_FIRST,
        /** Registered latest first. */
        NEWEST_FIRST,
        /** Alphabetically by company, ignoring case. */
        COMPANY
    }

    private final String company;
    private final String position;
    private final Set<JobApplicationStatus> statuses;
    private final Instant registeredAfter;
    private final Sort sort;

    private ApplicationQuery(String company, String position, Set<JobApplicationStatus> statuses,
                             Instant registeredAfter, Sort sort) {
        this.company = company;
        this.position = position;
        this.statuses = statuses;
        this.registeredAfter = registeredAfter;
        this.sort = sort;
    }

    /** Matches every application, oldest first. */
    public static ApplicationQuery all() {
        return new ApplicationQuery(null, null, Set.of(), null, Sort.OLDEST_FIRST);
    }

    /** Keeps applications whose company contains {@code keyword}, ignoring case. */
    public ApplicationQuery company(String keyword) {
        return new ApplicationQuery(requireKeyword(keyword, "Company keyword"),
                position, statuses, registeredAfter, sort);
    }

    /** Keeps applications whose position contains {@code keyword}, ignoring case. */
    public ApplicationQuery position(String keyword) {
        return new ApplicationQuery(company, requireKeyword(keyword, "Position keyword"),
                statuses, registeredAfter, sort);
    }

    /** Keeps applications sitting at any of {@code wanted}. Calling it with none matches every status. */
    public ApplicationQuery statuses(JobApplicationStatus... wanted) {
        Objects.requireNonNull(wanted, "Statuses are required");
        Arrays.stream(wanted).forEach(status -> Objects.requireNonNull(status, "A status must not be null"));
        Set<JobApplicationStatus> chosen = wanted.length == 0
                ? Set.of()
                : EnumSet.copyOf(Arrays.asList(wanted));
        return new ApplicationQuery(company, position, chosen, registeredAfter, sort);
    }

    /** Keeps applications registered strictly after {@code moment}. */
    public ApplicationQuery registeredAfter(Instant moment) {
        return new ApplicationQuery(company, position, statuses,
                Objects.requireNonNull(moment, "Moment is required"), sort);
    }

    public ApplicationQuery sortedBy(Sort order) {
        return new ApplicationQuery(company, position, statuses, registeredAfter,
                Objects.requireNonNull(order, "Sort order is required"));
    }

    /** Whether {@code application} satisfies every filter set on this query. */
    public boolean matches(JobApplication application) {
        Objects.requireNonNull(application, "Application is required");
        return matchesCompany(application)
                && matchesPosition(application)
                && matchesStatus(application)
                && matchesRegisteredAfter(application);
    }

    /**
     * The order results should come back in.
     *
     * <p>The comparator treats equal keys as equal rather than inventing a
     * tie-break, so sorting a list with {@code List.sort} keeps applications
     * that tie in the order the repository handed them over.
     */
    public Comparator<JobApplication> comparator() {
        return switch (sort) {
            case OLDEST_FIRST -> Comparator.comparing(JobApplication::createdAt);
            case NEWEST_FIRST -> Comparator.comparing(JobApplication::createdAt).reversed();
            case COMPANY -> Comparator.comparing(JobApplication::company, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private boolean matchesCompany(JobApplication application) {
        return company == null || containsIgnoringCase(application.company(), company);
    }

    private boolean matchesPosition(JobApplication application) {
        return position == null || containsIgnoringCase(application.position(), position);
    }

    private boolean matchesStatus(JobApplication application) {
        return statuses.isEmpty() || statuses.contains(application.status());
    }

    private boolean matchesRegisteredAfter(JobApplication application) {
        return registeredAfter == null || application.createdAt().isAfter(registeredAfter);
    }

    private static boolean containsIgnoringCase(String value, String keyword) {
        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    /**
     * A blank keyword is refused rather than quietly ignored: silently dropping a
     * filter would return far more than the caller asked for and look like a bug
     * in the data.
     */
    private static String requireKeyword(String keyword, String field) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
        return keyword.strip();
    }
}
