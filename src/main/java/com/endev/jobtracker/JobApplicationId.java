package com.endev.jobtracker;

/**
 * Identifier of a single job application, e.g. "APP-2026-014".
 *
 * <p>The value is validated at construction time, so an application can never
 * exist without a usable id.
 */
public record JobApplicationId(String value) {

    public JobApplicationId {
        if (value == null) {
            throw new IllegalArgumentException("Job application id must not be null");
        }
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Job application id must not be blank");
        }
    }

    public static JobApplicationId of(String value) {
        return new JobApplicationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
