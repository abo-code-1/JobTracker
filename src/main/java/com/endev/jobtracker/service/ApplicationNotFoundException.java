package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplicationId;

/** Thrown when an operation names an application the tracker does not hold. */
public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(JobApplicationId id) {
        super("No job application with id " + id);
    }
}
