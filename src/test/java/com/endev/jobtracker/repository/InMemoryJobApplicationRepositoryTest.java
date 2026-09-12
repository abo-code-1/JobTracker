package com.endev.jobtracker.repository;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryJobApplicationRepositoryTest {

    private JobApplicationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryJobApplicationRepository();
    }

    private static JobApplication application(String id, String company) {
        return JobApplication.draft(new JobApplicationId(id), company, "Java Intern");
    }

    @Test
    @DisplayName("a saved application can be found by its id")
    void savedApplicationCanBeFound() {
        repository.save(application("APP-1", "Kaspi"));

        Optional<JobApplication> found = repository.findById(new JobApplicationId("APP-1"));

        assertTrue(found.isPresent());
        assertEquals("Kaspi", found.get().company());
    }

    @Test
    @DisplayName("an unknown id yields an empty result rather than null")
    void unknownIdYieldsEmpty() {
        assertTrue(repository.findById(new JobApplicationId("APP-404")).isEmpty());
        assertFalse(repository.existsById(new JobApplicationId("APP-404")));
    }

    @Test
    @DisplayName("saving the same id twice replaces the application instead of duplicating it")
    void savingSameIdReplaces() {
        repository.save(application("APP-1", "Kaspi"));
        repository.save(application("APP-1", "Halyk"));

        assertEquals(1, repository.count());
        assertEquals("Halyk", repository.findById(new JobApplicationId("APP-1")).orElseThrow().company());
    }

    @Test
    @DisplayName("findAll keeps the order the applications were saved in")
    void findAllKeepsInsertionOrder() {
        repository.save(application("APP-1", "Kaspi"));
        repository.save(application("APP-2", "Halyk"));
        repository.save(application("APP-3", "Freedom"));

        List<String> companies = repository.findAll().stream().map(JobApplication::company).toList();

        assertEquals(List.of("Kaspi", "Halyk", "Freedom"), companies);
    }

    @Test
    @DisplayName("findByStatus returns only the matching applications")
    void findByStatusFilters() {
        JobApplication applied = application("APP-1", "Kaspi");
        applied.moveTo(JobApplicationStatus.APPLIED);
        repository.save(applied);
        repository.save(application("APP-2", "Halyk"));

        assertEquals(1, repository.findByStatus(JobApplicationStatus.APPLIED).size());
        assertEquals(1, repository.findByStatus(JobApplicationStatus.DRAFT).size());
        assertEquals(0, repository.findByStatus(JobApplicationStatus.OFFER).size());
    }

    @Test
    @DisplayName("deleting reports whether anything was removed")
    void deleteReportsWhetherAnythingWasRemoved() {
        repository.save(application("APP-1", "Kaspi"));

        assertTrue(repository.deleteById(new JobApplicationId("APP-1")));
        assertFalse(repository.deleteById(new JobApplicationId("APP-1")));
        assertEquals(0, repository.count());
    }

    @Test
    @DisplayName("the returned list is a copy, so callers cannot modify the store")
    void returnedListIsACopy() {
        repository.save(application("APP-1", "Kaspi"));

        List<JobApplication> all = repository.findAll();

        assertThrows(UnsupportedOperationException.class, () -> all.add(application("APP-2", "Halyk")));
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("null arguments are rejected")
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> repository.save(null));
        assertThrows(NullPointerException.class, () -> repository.findById(null));
        assertThrows(NullPointerException.class, () -> repository.findByStatus(null));
        assertThrows(NullPointerException.class, () -> repository.deleteById(null));
    }
}
