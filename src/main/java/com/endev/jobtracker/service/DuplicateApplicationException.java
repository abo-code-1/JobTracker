package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplicationId;

/**
 * Thrown when registering an id the tracker already holds.
 *
 * <p>Overwriting silently would lose the existing application's status and its
 * whole history, so registration refuses instead.
 */
public class DuplicateApplicationException extends RuntimeException {

    public DuplicateApplicationException(JobApplicationId id) {
        super("A job application with id " + id + " already exists");
    }
}
