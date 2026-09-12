package com.endev.jobtracker.repository;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;

import java.util.List;
import java.util.Optional;

/**
 * Storage for job applications.
 *
 * <p>The rest of the tracker depends on this interface rather than on a concrete
 * store, so the in-memory implementation used here can later be swapped for a
 * file or a database without touching the domain or the service.
 */
public interface JobApplicationRepository {

    /** Stores the application, replacing any existing one with the same id. */
    void save(JobApplication application);

    Optional<JobApplication> findById(JobApplicationId id);

    /** Every stored application, in the order it was first saved. */
    List<JobApplication> findAll();

    List<JobApplication> findByStatus(JobApplicationStatus status);

    boolean existsById(JobApplicationId id);

    /** @return {@code true} if an application was removed, {@code false} if there was none */
    boolean deleteById(JobApplicationId id);

    long count();
}
