package com.endev.jobtracker.service;

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

class CompanyReportTest {

    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");

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

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        repository = new InMemoryJobApplicationRepository();
        service = new JobApplicationService(repository, clock);
    }

    private static JobApplicationId id(String value) {
        return new JobApplicationId(value);
    }

    private CompanyReport report() {
        return CompanyReport.of(repository.findAll());
    }

    @Test
    @DisplayName("an empty tracker breaks down into no companies")
    void emptyTrackerHasNoCompanies() {
        assertEquals(0, report().companies());
        assertTrue(report().byCompany().isEmpty());
    }

    @Test
    @DisplayName("applications are grouped per company with their status breakdown")
    void applicationsAreGroupedPerCompany() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.register(id("APP-2"), "Kaspi", "QA Engineer");
        service.register(id("APP-3"), "Halyk", "Analyst");
        service.submit(id("APP-2"));

        CompanyStats kaspi = report().forCompany("Kaspi").orElseThrow();

        assertEquals(2, kaspi.total());
        assertEquals(1, kaspi.countOf(JobApplicationStatus.DRAFT));
        assertEquals(1, kaspi.countOf(JobApplicationStatus.APPLIED));
        assertEquals(2, report().companies());
    }

    @Test
    @DisplayName("the same company spelled differently is still one company")
    void sameCompanySpelledDifferentlyIsOneCompany() {
        service.register(id("APP-1"), "Kaspi Bank", "Java Intern");
        service.register(id("APP-2"), "kaspi bank", "QA Engineer");
        service.register(id("APP-3"), "KASPI BANK", "Analyst");

        assertEquals(1, report().companies());
        CompanyStats kaspi = report().byCompany().get(0);
        assertEquals(3, kaspi.total());
        assertEquals("Kaspi Bank", kaspi.company(), "the first spelling seen is the one shown");
    }

    @Test
    @DisplayName("the busiest company comes first, ties break alphabetically")
    void busiestCompanyComesFirst() {
        service.register(id("APP-1"), "Zenith", "Analyst");
        service.register(id("APP-2"), "Kaspi", "Java Intern");
        service.register(id("APP-3"), "Kaspi", "QA Engineer");
        service.register(id("APP-4"), "Alpha", "Designer");

        List<String> order = report().byCompany().stream().map(CompanyStats::company).toList();

        assertEquals(List.of("Kaspi", "Alpha", "Zenith"), order);
    }

    @Test
    @DisplayName("the average reply time is measured from submission to the company's answer")
    void averageReplyTimeIsMeasuredFromSubmission() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));
        clock.advanceDays(4);
        service.changeStatus(id("APP-1"), JobApplicationStatus.INTERVIEWING);

        service.register(id("APP-2"), "Kaspi", "QA Engineer");
        service.submit(id("APP-2"));
        clock.advanceDays(6);
        service.changeStatus(id("APP-2"), JobApplicationStatus.REJECTED);

        CompanyStats kaspi = report().forCompany("Kaspi").orElseThrow();

        assertEquals(2, kaspi.repliesObserved());
        assertEquals(5.0, kaspi.averageDaysToFirstReply().orElseThrow(), 1e-9);
    }

    @Test
    @DisplayName("a company that never answered has no average rather than an average of zero")
    void companyThatNeverAnsweredHasNoAverage() {
        service.register(id("APP-1"), "Halyk", "Analyst");
        service.submit(id("APP-1"));
        clock.advanceDays(90);

        CompanyStats halyk = report().forCompany("Halyk").orElseThrow();

        assertFalse(halyk.hasReplied());
        assertTrue(halyk.averageDaysToFirstReply().isEmpty());
        assertEquals(0, halyk.repliesObserved());
    }

    @Test
    @DisplayName("withdrawing is not a reply: the candidate moved, not the company")
    void withdrawingIsNotAReply() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));
        clock.advanceDays(30);
        service.withdraw(id("APP-1"));

        assertFalse(report().forCompany("Kaspi").orElseThrow().hasReplied());
    }

    @Test
    @DisplayName("only the companies that answered count towards their own average")
    void onlyAnsweringApplicationsCountTowardsTheAverage() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));
        clock.advanceDays(3);
        service.changeStatus(id("APP-1"), JobApplicationStatus.REJECTED);

        service.register(id("APP-2"), "Kaspi", "QA Engineer");
        service.submit(id("APP-2"));
        clock.advanceDays(100);

        CompanyStats kaspi = report().forCompany("Kaspi").orElseThrow();

        assertEquals(2, kaspi.total());
        assertEquals(1, kaspi.repliesObserved());
        assertEquals(3.0, kaspi.averageDaysToFirstReply().orElseThrow(), 1e-9);
    }

    @Test
    @DisplayName("offers count accepted offers too, and open leaves out closed applications")
    void offersAndOpenCounts() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");
        service.submit(id("APP-1"));
        service.changeStatus(id("APP-1"), JobApplicationStatus.INTERVIEWING);
        service.changeStatus(id("APP-1"), JobApplicationStatus.OFFER);
        service.changeStatus(id("APP-1"), JobApplicationStatus.ACCEPTED);

        service.register(id("APP-2"), "Kaspi", "QA Engineer");
        service.submit(id("APP-2"));
        service.changeStatus(id("APP-2"), JobApplicationStatus.REJECTED);

        service.register(id("APP-3"), "Kaspi", "Analyst");

        CompanyStats kaspi = report().forCompany("Kaspi").orElseThrow();

        assertEquals(1, kaspi.offers());
        assertEquals(1, kaspi.open());
    }

    @Test
    @DisplayName("an unknown company is reported as absent rather than as an empty row")
    void unknownCompanyIsAbsent() {
        service.register(id("APP-1"), "Kaspi", "Java Intern");

        assertTrue(report().forCompany("Google").isEmpty());
        assertTrue(report().forCompany("  kaspi  ").isPresent(), "lookup ignores case and padding");
    }

    @Test
    @DisplayName("missing arguments are refused")
    void missingArgumentsAreRefused() {
        assertThrows(NullPointerException.class, () -> CompanyReport.of(null));
        assertThrows(NullPointerException.class, () -> report().forCompany(null));
    }
}
