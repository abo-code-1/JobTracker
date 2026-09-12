package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.InMemoryJobApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationQueryTest {

    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");

    /** Advances a day per reading, so each registered application gets its own moment. */
    private static final class SteppingClock extends Clock {
        private Instant now = START;

        @Override
        public Instant instant() {
            Instant current = now;
            now = now.plusSeconds(86_400);
            return current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(new InMemoryJobApplicationRepository(), new SteppingClock());

        service.register(new JobApplicationId("APP-1"), "Kaspi Bank", "Java Intern");
        service.register(new JobApplicationId("APP-2"), "Halyk Bank", "Backend Engineer");
        service.register(new JobApplicationId("APP-3"), "Freedom Finance", "Java Developer");
        service.register(new JobApplicationId("APP-4"), "Jusan", "QA Engineer");

        service.submit(new JobApplicationId("APP-2"));
        service.submit(new JobApplicationId("APP-3"));
        service.changeStatus(new JobApplicationId("APP-3"), JobApplicationStatus.INTERVIEWING);
    }

    private List<String> idsOf(List<JobApplication> applications) {
        return applications.stream().map(application -> application.id().toString()).toList();
    }

    @Test
    @DisplayName("an empty query returns everything, oldest first")
    void emptyQueryReturnsEverythingOldestFirst() {
        assertEquals(List.of("APP-1", "APP-2", "APP-3", "APP-4"),
                idsOf(service.search(ApplicationQuery.all())));
    }

    @Test
    @DisplayName("the company filter matches part of the name, ignoring case")
    void companyFilterMatchesPartOfTheNameIgnoringCase() {
        assertEquals(List.of("APP-1"), idsOf(service.search(ApplicationQuery.all().company("kaspi"))));
        assertEquals(List.of("APP-1", "APP-2"), idsOf(service.search(ApplicationQuery.all().company("BANK"))));
    }

    @Test
    @DisplayName("the position filter matches part of the title, ignoring case")
    void positionFilterMatchesPartOfTheTitleIgnoringCase() {
        assertEquals(List.of("APP-1", "APP-3"), idsOf(service.search(ApplicationQuery.all().position("java"))));
        assertEquals(List.of("APP-2", "APP-4"),
                idsOf(service.search(ApplicationQuery.all().position("engineer"))));
    }

    @Test
    @DisplayName("the status filter accepts several statuses at once")
    void statusFilterAcceptsSeveralStatuses() {
        assertEquals(List.of("APP-2", "APP-3"), idsOf(service.search(ApplicationQuery.all()
                .statuses(JobApplicationStatus.APPLIED, JobApplicationStatus.INTERVIEWING))));
        assertEquals(List.of("APP-1", "APP-4"), idsOf(service.search(ApplicationQuery.all()
                .statuses(JobApplicationStatus.DRAFT))));
    }

    @Test
    @DisplayName("asking for no particular status matches every status")
    void noParticularStatusMatchesEverything() {
        assertEquals(4, service.search(ApplicationQuery.all().statuses()).size());
    }

    @Test
    @DisplayName("registeredAfter keeps only the later applications, and is strict")
    void registeredAfterIsStrict() {
        assertEquals(List.of("APP-3", "APP-4"),
                idsOf(service.search(ApplicationQuery.all().registeredAfter(START.plusSeconds(86_400)))));
        assertEquals(List.of("APP-2", "APP-3", "APP-4"),
                idsOf(service.search(ApplicationQuery.all().registeredAfter(START))));
    }

    @Test
    @DisplayName("filters combine: every one of them has to match")
    void filtersCombine() {
        List<JobApplication> found = service.search(ApplicationQuery.all()
                .position("java")
                .statuses(JobApplicationStatus.INTERVIEWING));

        assertEquals(List.of("APP-3"), idsOf(found));
    }

    @Test
    @DisplayName("a query that matches nothing returns an empty list, never null")
    void queryMatchingNothingReturnsEmptyList() {
        List<JobApplication> found = service.search(ApplicationQuery.all().company("Google"));

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("results can be sorted newest first or by company name")
    void resultsCanBeSorted() {
        assertEquals(List.of("APP-4", "APP-3", "APP-2", "APP-1"),
                idsOf(service.search(ApplicationQuery.all().sortedBy(ApplicationQuery.Sort.NEWEST_FIRST))));

        assertEquals(List.of("APP-3", "APP-2", "APP-4", "APP-1"),
                idsOf(service.search(ApplicationQuery.all().sortedBy(ApplicationQuery.Sort.COMPANY))));
    }

    @Test
    @DisplayName("sorting by company ignores case and keeps ties in registration order")
    void sortingByCompanyIgnoresCaseAndKeepsTies() {
        service.register(new JobApplicationId("APP-5"), "kaspi bank", "Analyst");
        service.register(new JobApplicationId("APP-6"), "KASPI BANK", "Designer");

        List<String> found = idsOf(service.search(ApplicationQuery.all()
                .company("kaspi")
                .sortedBy(ApplicationQuery.Sort.COMPANY)));

        assertEquals(List.of("APP-1", "APP-5", "APP-6"), found);
    }

    @Test
    @DisplayName("a query is immutable, so building on one does not change it")
    void queryIsImmutable() {
        ApplicationQuery everything = ApplicationQuery.all();
        ApplicationQuery narrowed = everything.company("Kaspi");

        assertEquals(4, service.search(everything).size());
        assertEquals(1, service.search(narrowed).size());
    }

    @Test
    @DisplayName("a blank keyword is refused rather than quietly matching everything")
    void blankKeywordIsRefused() {
        ApplicationQuery query = ApplicationQuery.all();

        assertThrows(IllegalArgumentException.class, () -> query.company("   "));
        assertThrows(IllegalArgumentException.class, () -> query.company(null));
        assertThrows(IllegalArgumentException.class, () -> query.position(""));
    }

    @Test
    @DisplayName("missing arguments are refused")
    void missingArgumentsAreRefused() {
        ApplicationQuery query = ApplicationQuery.all();

        assertThrows(NullPointerException.class, () -> query.registeredAfter(null));
        assertThrows(NullPointerException.class, () -> query.sortedBy(null));
        assertThrows(NullPointerException.class, () -> query.statuses((JobApplicationStatus[]) null));
        assertThrows(NullPointerException.class, () -> query.statuses(JobApplicationStatus.DRAFT, null));
        assertThrows(NullPointerException.class, () -> service.search(null));
    }
}
