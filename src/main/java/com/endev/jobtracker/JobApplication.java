package com.endev.jobtracker;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One job application: a candidate applying to one opening at one company.
 *
 * <p>The application owns its own status and refuses to change it in a way the
 * {@link JobApplicationPolicy} forbids, so an instance can never hold a status it
 * could not legally have reached. Every accepted change is recorded in
 * {@link #history()}.
 *
 * <p>The clock is a constructor argument rather than a call to {@code Instant.now()}
 * so tests can pin time down instead of guessing at it.
 */
public final class JobApplication {

    private static final JobApplicationPolicy POLICY = new JobApplicationPolicy();

    private final JobApplicationId id;
    private final String company;
    private final String position;
    private final Clock clock;
    private final Instant createdAt;
    private final List<StatusChange> history = new ArrayList<>();

    private JobApplicationStatus status;

    private JobApplication(JobApplicationId id, String company, String position,
                           JobApplicationStatus status, Clock clock) {
        this.id = Objects.requireNonNull(id, "Job application id is required");
        this.company = requireText(company, "Company");
        this.position = requireText(position, "Position");
        this.status = Objects.requireNonNull(status, "Status is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.createdAt = clock.instant();
    }

    /** Starts a new application that has been written up but not sent yet. */
    public static JobApplication draft(JobApplicationId id, String company, String position) {
        return draft(id, company, position, Clock.systemUTC());
    }

    public static JobApplication draft(JobApplicationId id, String company, String position, Clock clock) {
        return new JobApplication(id, company, position, JobApplicationStatus.DRAFT, clock);
    }

    /**
     * Rebuilds an application that is already at a known stage, for example when
     * loading it back from storage. The rebuilt application starts with an empty
     * history: only changes made through this object are recorded.
     */
    public static JobApplication at(JobApplicationId id, String company, String position,
                                    JobApplicationStatus status) {
        return at(id, company, position, status, Clock.systemUTC());
    }

    public static JobApplication at(JobApplicationId id, String company, String position,
                                    JobApplicationStatus status, Clock clock) {
        return new JobApplication(id, company, position, status, clock);
    }

    /**
     * Moves the application to {@code target} and records the change.
     *
     * @throws IllegalStateException if the workflow forbids the change
     */
    public void moveTo(JobApplicationStatus target) {
        JobApplicationStatus previous = this.status;
        this.status = POLICY.move(previous, target);
        this.history.add(new StatusChange(previous, this.status, clock.instant()));
    }

    /** Whether {@link #moveTo(JobApplicationStatus)} would succeed for {@code target}. */
    public boolean canMoveTo(JobApplicationStatus target) {
        return target != null && POLICY.allowedFrom(status).contains(target);
    }

    /** Whether the application has reached a final status and can no longer move. */
    public boolean isClosed() {
        return POLICY.allowedFrom(status).isEmpty();
    }

    /** Every accepted status change, oldest first. Rejected changes are not recorded. */
    public List<StatusChange> history() {
        return List.copyOf(history);
    }

    /** When the status last changed, or empty while the application has never moved. */
    public Optional<Instant> lastChangedAt() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.get(history.size() - 1).at());
    }

    public JobApplicationId id() {
        return id;
    }

    public String company() {
        return company;
    }

    public String position() {
        return position;
    }

    public JobApplicationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
        return value.strip();
    }

    /** Two applications are the same application when they share an id. */
    @Override
    public boolean equals(Object other) {
        return other instanceof JobApplication that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s %s at %s [%s]".formatted(id, position, company, status);
    }
}
