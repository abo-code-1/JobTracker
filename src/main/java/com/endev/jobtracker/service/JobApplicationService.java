package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.JobApplicationRepository;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The operations a user performs on the tracker: register an application, move
 * it through the pipeline, look it up, and see how the whole pipeline is doing.
 *
 * <p>The service coordinates; it does not re-implement the workflow. Whether a
 * status change is legal stays the policy's decision, reached through the
 * application itself.
 */
public final class JobApplicationService {

    private final JobApplicationRepository repository;
    private final Clock clock;

    public JobApplicationService(JobApplicationRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public JobApplicationService(JobApplicationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    /**
     * Registers a new application as a draft.
     *
     * @throws DuplicateApplicationException if the id is already taken
     */
    public JobApplication register(JobApplicationId id, String company, String position) {
        Objects.requireNonNull(id, "Job application id is required");
        if (repository.existsById(id)) {
            throw new DuplicateApplicationException(id);
        }
        JobApplication application = JobApplication.draft(id, company, position, clock);
        repository.save(application);
        return application;
    }

    /**
     * Moves a stored application to {@code target}.
     *
     * @throws ApplicationNotFoundException if no such application is stored
     * @throws IllegalStateException        if the workflow forbids the change
     */
    public JobApplication changeStatus(JobApplicationId id, JobApplicationStatus target) {
        JobApplication application = require(id);
        application.moveTo(target);
        repository.save(application);
        return application;
    }

    /** Sends a draft off to the company. */
    public JobApplication submit(JobApplicationId id) {
        return changeStatus(id, JobApplicationStatus.APPLIED);
    }

    /** Pulls out of the process, whatever stage it had reached. */
    public JobApplication withdraw(JobApplicationId id) {
        return changeStatus(id, JobApplicationStatus.WITHDRAWN);
    }

    /**
     * @throws ApplicationNotFoundException if no such application is stored
     */
    public JobApplication find(JobApplicationId id) {
        return require(id);
    }

    public List<JobApplication> findAll() {
        return repository.findAll();
    }

    public List<JobApplication> findByStatus(JobApplicationStatus status) {
        return repository.findByStatus(status);
    }

    /** Applications that can still move, in the order they were registered. */
    public List<JobApplication> findOpen() {
        return repository.findAll().stream()
                .filter(application -> !application.isClosed())
                .toList();
    }

    /** A snapshot of how many applications sit at each status, and the resulting rates. */
    public PipelineReport report() {
        List<JobApplication> all = repository.findAll();
        Map<JobApplicationStatus, Long> counts = new EnumMap<>(JobApplicationStatus.class);
        for (JobApplication application : all) {
            counts.merge(application.status(), 1L, Long::sum);
        }
        return new PipelineReport(all.size(), counts);
    }

    private JobApplication require(JobApplicationId id) {
        Objects.requireNonNull(id, "Job application id is required");
        return repository.findById(id).orElseThrow(() -> new ApplicationNotFoundException(id));
    }
}
