package com.endev.jobtracker.cli;

import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.InMemoryJobApplicationRepository;
import com.endev.jobtracker.repository.JobApplicationRepository;
import com.endev.jobtracker.service.CompanyReport;
import com.endev.jobtracker.service.JobApplicationService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyBreakdownPrinterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-01T09:00:00Z");

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
    private PipelinePrinter printer;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        repository = new InMemoryJobApplicationRepository();
        service = new JobApplicationService(repository, clock);
        printer = new PipelinePrinter();
    }

    private String render() {
        return printer.renderByCompany(CompanyReport.of(repository.findAll()));
    }

    @Test
    @DisplayName("with nothing tracked the breakdown says so")
    void nothingTrackedSaysSo() {
        assertTrue(render().contains("Nothing to break down yet."), render());
    }

    @Test
    @DisplayName("a company that answered shows its average reply time to one decimal")
    void answeringCompanyShowsAverage() {
        service.register(new JobApplicationId("APP-1"), "Kaspi Bank", "Java Intern");
        service.submit(new JobApplicationId("APP-1"));
        clock.advanceDays(3);
        service.changeStatus(new JobApplicationId("APP-1"), JobApplicationStatus.INTERVIEWING);

        service.register(new JobApplicationId("APP-2"), "Kaspi Bank", "QA Engineer");
        service.submit(new JobApplicationId("APP-2"));
        clock.advanceDays(4);
        service.changeStatus(new JobApplicationId("APP-2"), JobApplicationStatus.REJECTED);

        String row = rowFor("Kaspi Bank");

        assertTrue(row.contains("3.5 days"), row);  // replies after 3 and 4 days
    }

    @Test
    @DisplayName("a silent company is named as such, not shown as answering in zero days")
    void silentCompanyIsNamedAsSuch() {
        service.register(new JobApplicationId("APP-1"), "Halyk", "Analyst");
        service.submit(new JobApplicationId("APP-1"));
        clock.advanceDays(60);

        String row = rowFor("Halyk");

        assertTrue(row.contains("no reply yet"), row);
        assertTrue(!row.contains("0.0 days"), row);
    }

    @Test
    @DisplayName("columns line up whatever the company name length")
    void columnsLineUp() {
        service.register(new JobApplicationId("APP-1"), "Kaspi", "Java Intern");
        service.register(new JobApplicationId("APP-2"), "Freedom Finance Group", "Analyst");

        List<String> lines = render().lines().toList();
        String header = lines.stream().filter(line -> line.startsWith("COMPANY")).findFirst().orElseThrow();

        assertEquals(header.indexOf("TOTAL"), rowFor("Kaspi").indexOf("1"));
        assertEquals(header.indexOf("TOTAL"), rowFor("Freedom Finance Group").indexOf("1"));
    }

    private String rowFor(String company) {
        return render().lines()
                .filter(line -> line.startsWith(company))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + company + " in:\n" + render()));
    }
}
