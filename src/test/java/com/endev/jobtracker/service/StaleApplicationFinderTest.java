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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaleApplicationFinderTest {

    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");

    /** A clock the test moves forward on purpose, so waiting can be simulated exactly. */
    private static final class MutableClock extends Clock {
        private Instant now = START;

        void advanceDays(long days) {
            now = now.plus(Duration.ofDays(days));
        }

        @Override
        public Instant instant() {
            return now;
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

    private MutableClock clock;
    private JobApplicationRepository repository;
    private JobApplicationService service;
    private StaleApplicationFinder finder;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        repository = new InMemoryJobApplicationRepository();
        service = new JobApplicationService(repository, clock);
        finder = new StaleApplicationFinder(repository, clock);
    }

    private static JobApplicationId id(String value) {
        return new JobApplicationId(value);
    }

    @Test
    @DisplayName("nothing is stale in a tracker where nothing has been sent")
    void nothingIsStaleWhenNothingWasSent() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        clock.advanceDays(90);

        assertTrue(finder.findStalerThan(14).isEmpty());
    }

    @Test
    @DisplayName("a draft never goes stale: it is waiting on the candidate, not the company")
    void draftNeverGoesStale() {
        JobApplication draft = service.register(id("APP-1"), "Kaspi", "Java Intern");
        clock.advanceDays(365);

        assertFalse(finder.isStale(draft, 14));
    }

    @Test
    @DisplayName("silence counts from the day the application was sent, at the exact boundary")
    void silenceCountsFromTheDayItWasSent() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));

        clock.advanceDays(13);
        assertTrue(finder.findStalerThan(14).isEmpty(), "13 days is not yet stale");

        clock.advanceDays(1);
        assertEquals(1, finder.findStalerThan(14).size(), "14 days is stale");
    }

    @Test
    @DisplayName("an application in an interview loop can also go quiet")
    void interviewingCanGoQuiet() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));
        service.changeStatus(id("APP-1"), JobApplicationStatus.INTERVIEWING);

        clock.advanceDays(30);

        List<WaitingApplication> stale = finder.findStalerThan(14);
        assertEquals(1, stale.size());
        assertEquals(JobApplicationStatus.INTERVIEWING, stale.get(0).application().status());
    }

    @Test
    @DisplayName("a closed application is never stale, however old it is")
    void closedApplicationIsNeverStale() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));
        service.changeStatus(id("APP-1"), JobApplicationStatus.REJECTED);
        service.register(id("APP-2"), "Halyk", "QA Engineer");
        service.submit(id("APP-2"));
        service.withdraw(id("APP-2"));

        clock.advanceDays(365);

        assertTrue(finder.findStalerThan(14).isEmpty());
    }

    @Test
    @DisplayName("the wait is measured from the last change, not from registration")
    void waitIsMeasuredFromTheLastChange() {
        JobApplication application = service.register(id("APP-1"), "Kaspi", "Java Intern");
        clock.advanceDays(60);
        service.submit(id("APP-1"));
        clock.advanceDays(5);

        assertEquals(5, finder.daysWaiting(application));
        assertTrue(finder.findStalerThan(14).isEmpty());
    }

    @Test
    @DisplayName("an application without history falls back to when it was registered")
    void applicationWithoutHistoryUsesRegistrationTime() {
        JobApplication restored = JobApplication.at(id("APP-1"), "Kaspi", "Java Intern",
                JobApplicationStatus.APPLIED, clock);
        repository.save(restored);

        clock.advanceDays(20);

        assertEquals(20, finder.daysWaiting(restored));
        assertEquals(1, finder.findStalerThan(14).size());
    }

    @Test
    @DisplayName("the longest silence is reported first")
    void longestSilenceComesFirst() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));
        clock.advanceDays(40);

        service.register(id("APP-2"), "Halyk", "QA Engineer");
        service.submit(id("APP-2"));
        clock.advanceDays(20);

        service.register(id("APP-3"), "Freedom", "Analyst");
        service.submit(id("APP-3"));
        clock.advanceDays(10);

        List<WaitingApplication> stale = finder.findStalerThan(14);

        assertEquals(List.of("APP-1", "APP-2"),
                stale.stream().map(waiting -> waiting.application().id().toString()).toList());
        assertEquals(70, stale.get(0).daysWaiting());
        assertEquals(30, stale.get(1).daysWaiting());
    }

    @Test
    @DisplayName("a threshold below one day is refused")
    void thresholdBelowOneDayIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> finder.findStalerThan(0));
        assertThrows(IllegalArgumentException.class, () -> finder.findStalerThan(-3));
    }

    @Test
    @DisplayName("a waiting application describes itself in readable terms")
    void waitingApplicationDescribesItself() {
        JobApplication application = service.register(id("APP-1"), "Kaspi", "Java Intern");

        assertEquals("Java Intern at Kaspi — 1 day with no answer",
                new WaitingApplication(application, 1).toString());
        assertEquals("Java Intern at Kaspi — 21 days with no answer",
                new WaitingApplication(application, 21).toString());
        assertThrows(IllegalArgumentException.class, () -> new WaitingApplication(application, -1));
    }

    @Test
    @DisplayName("the finder refuses to be built without its collaborators")
    void finderRequiresCollaborators() {
        assertThrows(NullPointerException.class, () -> new StaleApplicationFinder(null, clock));
        assertThrows(NullPointerException.class, () -> new StaleApplicationFinder(repository, null));
    }
}
