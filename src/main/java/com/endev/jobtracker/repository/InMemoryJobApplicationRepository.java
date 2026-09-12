package com.endev.jobtracker.repository;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps job applications in memory for the lifetime of the program.
 *
 * <p>Insertion order is preserved so listings stay stable and tests can rely on
 * them. Returned lists are copies: callers cannot reach into the store.
 */
public final class InMemoryJobApplicationRepository implements JobApplicationRepository {

    private final Map<JobApplicationId, JobApplication> applications = new LinkedHashMap<>();

    @Override
    public void save(JobApplication application) {
        Objects.requireNonNull(application, "Application is required");
        applications.put(application.id(), application);
    }

    @Override
    public Optional<JobApplication> findById(JobApplicationId id) {
        Objects.requireNonNull(id, "Job application id is required");
        return Optional.ofNullable(applications.get(id));
    }

    @Override
    public List<JobApplication> findAll() {
        return List.copyOf(applications.values());
    }

    @Override
    public List<JobApplication> findByStatus(JobApplicationStatus status) {
        Objects.requireNonNull(status, "Status is required");
        return applications.values().stream()
                .filter(application -> application.status() == status)
                .toList();
    }

    @Override
    public boolean existsById(JobApplicationId id) {
        Objects.requireNonNull(id, "Job application id is required");
        return applications.containsKey(id);
    }

    @Override
    public boolean deleteById(JobApplicationId id) {
        Objects.requireNonNull(id, "Job application id is required");
        return applications.remove(id) != null;
    }

    @Override
    public long count() {
        return applications.size();
    }
}
