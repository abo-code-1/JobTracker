package com.endev.jobtracker.service;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.InMemoryJobApplicationRepository;
import com.endev.jobtracker.repository.JobApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneOffset.UTC);

    private JobApplicationRepository repository;
    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryJobApplicationRepository();
        service = new JobApplicationService(repository, CLOCK);
    }

    private static JobApplicationId id(String value) {
        return new JobApplicationId(value);
    }

    @Test
    @DisplayName("a registered application is stored as a draft")
    void registeredApplicationIsStoredAsDraft() {
        JobApplication registered = service.register(id("APP-1"), "Kaspi", "Java Intern");

        assertEquals(JobApplicationStatus.DRAFT, registered.status());
        assertEquals(1, repository.count());
        assertEquals(registered, service.find(id("APP-1")));
    }

    @Test
    @DisplayName("registering an id twice is refused so the first application is not lost")
    void registeringSameIdTwiceIsRefused() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");

        assertThrows(DuplicateApplicationException.class,
                () -> service.register(id("APP-1"), "Halyk", "QA Engineer"));
        assertEquals("Kaspi", service.find(id("APP-1")).company());
    }

    @Test
    @DisplayName("an unknown id is reported rather than returning null")
    void unknownIdIsReported() {
        assertThrows(ApplicationNotFoundException.class, () -> service.find(id("APP-404")));
        assertThrows(ApplicationNotFoundException.class,
                () -> service.changeStatus(id("APP-404"), JobApplicationStatus.APPLIED));
    }

    @Test
    @DisplayName("the service moves an application through the pipeline")
    void serviceMovesApplicationThroughPipeline() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");

        service.submit(id("APP-1"));
        service.changeStatus(id("APP-1"), JobApplicationStatus.INTERVIEWING);
        JobApplication moved = service.changeStatus(id("APP-1"), JobApplicationStatus.OFFER);

        assertEquals(JobApplicationStatus.OFFER, moved.status());
        assertEquals(3, moved.history().size());
    }

    @Test
    @DisplayName("the service does not decide the rules itself: a forbidden move still throws")
    void forbiddenMoveStillThrows() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");

        assertThrows(IllegalStateException.class,
                () -> service.changeStatus(id("APP-1"), JobApplicationStatus.OFFER));
        assertEquals(JobApplicationStatus.DRAFT, service.find(id("APP-1")).status());
    }

    @Test
    @DisplayName("withdrawing closes the application at any open stage")
    void withdrawingClosesTheApplication() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));

        JobApplication withdrawn = service.withdraw(id("APP-1"));

        assertEquals(JobApplicationStatus.WITHDRAWN, withdrawn.status());
        assertTrue(withdrawn.isClosed());
    }

    @Test
    @DisplayName("findOpen leaves out everything already closed")
    void findOpenLeavesOutClosedApplications() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.register(id("APP-2"), "Halyk", "QA Engineer");
        service.register(id("APP-3"), "Freedom", "Analyst");
        service.submit(id("APP-2"));
        service.changeStatus(id("APP-2"), JobApplicationStatus.REJECTED);

        List<JobApplicationId> open = service.findOpen().stream().map(JobApplication::id).toList();

        assertEquals(List.of(id("APP-1"), id("APP-3")), open);
    }

    @Test
    @DisplayName("findByStatus asks the repository for one status only")
    void findByStatusFilters() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.register(id("APP-2"), "Halyk", "QA Engineer");
        service.submit(id("APP-2"));

        assertEquals(1, service.findByStatus(JobApplicationStatus.DRAFT).size());
        assertEquals(1, service.findByStatus(JobApplicationStatus.APPLIED).size());
    }

    @Test
    @DisplayName("the report counts every status and measures rates against submitted applications")
    void reportCountsStatusesAndRates() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");          // stays a draft
        service.register(id("APP-2"), "Halyk", "QA Engineer");
        service.register(id("APP-3"), "Freedom", "Analyst");
        service.register(id("APP-4"), "Jusan", "Backend Engineer");

        service.submit(id("APP-2"));
        service.changeStatus(id("APP-2"), JobApplicationStatus.REJECTED);
        service.submit(id("APP-3"));
        service.changeStatus(id("APP-3"), JobApplicationStatus.INTERVIEWING);
        service.changeStatus(id("APP-3"), JobApplicationStatus.OFFER);
        service.submit(id("APP-4"));

        PipelineReport report = service.report();

        assertEquals(4, report.total());
        assertEquals(3, report.submitted());
        assertEquals(1, report.countOf(JobApplicationStatus.DRAFT));
        assertEquals(1, report.countOf(JobApplicationStatus.REJECTED));
        assertEquals(1, report.countOf(JobApplicationStatus.OFFER));
        assertEquals(1, report.countOf(JobApplicationStatus.APPLIED));
        assertEquals(0, report.countOf(JobApplicationStatus.ACCEPTED));
        assertEquals(3, report.open());
        assertEquals(1.0 / 3, report.offerRate(), 1e-9);
        assertEquals(1.0 / 3, report.rejectionRate(), 1e-9);
    }

    @Test
    @DisplayName("an empty tracker reports zeroes instead of dividing by zero")
    void emptyTrackerReportsZeroes() {
        PipelineReport report = service.report();

        assertEquals(0, report.total());
        assertEquals(0, report.submitted());
        assertEquals(0.0, report.offerRate());
        assertEquals(0.0, report.rejectionRate());
        assertEquals(JobApplicationStatus.values().length, report.countsByStatus().size());
    }

    @Test
    @DisplayName("drafts never drag the rates down")
    void draftsDoNotAffectRates() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.register(id("APP-2"), "Halyk", "QA Engineer");
        service.submit(id("APP-2"));
        service.changeStatus(id("APP-2"), JobApplicationStatus.INTERVIEWING);
        service.changeStatus(id("APP-2"), JobApplicationStatus.OFFER);

        PipelineReport report = service.report();

        assertEquals(1, report.submitted());
        assertEquals(1.0, report.offerRate(), 1e-9);
    }

    @Test
    @DisplayName("the service refuses to be built without its collaborators")
    void serviceRequiresCollaborators() {
        assertThrows(NullPointerException.class, () -> new JobApplicationService(null));
        assertThrows(NullPointerException.class, () -> new JobApplicationService(repository, null));
    }
}
