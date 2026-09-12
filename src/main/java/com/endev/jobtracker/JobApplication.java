package com.endev.jobtracker;

import java.util.Objects;

/**
 * One job application: a candidate applying to one opening at one company.
 *
 * <p>The application owns its own status and refuses to change it in a way the
 * {@link JobApplicationPolicy} forbids, so an instance can never hold a status it
 * could not legally have reached.
 */
public final class JobApplication {

    private static final JobApplicationPolicy POLICY = new JobApplicationPolicy();

    private final JobApplicationId id;
    private final String company;
    private final String position;
    private JobApplicationStatus status;

    private JobApplication(JobApplicationId id, String company, String position, JobApplicationStatus status) {
        this.id = Objects.requireNonNull(id, "Job application id is required");
        this.company = requireText(company, "Company");
        this.position = requireText(position, "Position");
        this.status = Objects.requireNonNull(status, "Status is required");
    }

    /**
     * Starts a new application that has been written up but not sent yet.
     */
    public static JobApplication draft(JobApplicationId id, String company, String position) {
        return new JobApplication(id, company, position, JobApplicationStatus.DRAFT);
    }

    /**
     * Rebuilds an application that is already at a known stage, for example when
     * loading it back from storage.
     */
    public static JobApplication at(JobApplicationId id, String company, String position,
                                    JobApplicationStatus status) {
        return new JobApplication(id, company, position, status);
    }

    /**
     * Moves the application to {@code target}.
     *
     * @throws IllegalStateException if the workflow forbids the change
     */
    public void moveTo(JobApplicationStatus target) {
        this.status = POLICY.move(this.status, target);
    }

    /**
     * Whether {@link #moveTo(JobApplicationStatus)} would succeed for {@code target}.
     */
    public boolean canMoveTo(JobApplicationStatus target) {
        return target != null && POLICY.allowedFrom(status).contains(target);
    }

    /**
     * Whether the application has reached a final status and can no longer move.
     */
    public boolean isClosed() {
        return POLICY.allowedFrom(status).isEmpty();
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
