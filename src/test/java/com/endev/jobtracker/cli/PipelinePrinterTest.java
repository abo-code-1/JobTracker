package com.endev.jobtracker.cli;

import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.InMemoryJobApplicationRepository;
import com.endev.jobtracker.service.JobApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelinePrinterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneOffset.UTC);

    private JobApplicationService service;
    private PipelinePrinter printer;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(new InMemoryJobApplicationRepository(), CLOCK);
        printer = new PipelinePrinter();
    }

    private String render() {
        return printer.render(service.findAll(), service.report());
    }

    @Test
    @DisplayName("an empty tracker says so instead of printing an empty table")
    void emptyTrackerSaysSo() {
        String output = render();

        assertTrue(output.contains("No applications yet."), output);
        assertTrue(output.contains("offer rate    0%"), output);
    }

    @Test
    @DisplayName("each application gets a row with its company, position and status")
    void eachApplicationGetsARow() {
        service.register(new JobApplicationId("APP-1"), "Kaspi", "Java Intern");
        service.submit(new JobApplicationId("APP-1"));

        List<String> lines = render().lines().toList();
        String row = lines.stream()
                .filter(line -> line.startsWith("APP-1"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for APP-1 in:\n" + render()));

        assertTrue(row.contains("Kaspi"), row);
        assertTrue(row.contains("Java Intern"), row);
        assertTrue(row.endsWith("APPLIED"), row);
    }

    @Test
    @DisplayName("columns line up when the values have different lengths")
    void columnsLineUp() {
        service.register(new JobApplicationId("APP-1"), "Kaspi", "Java Intern");
        service.register(new JobApplicationId("APP-2026-002"), "Freedom Finance", "Backend Engineer");

        List<String> lines = render().lines().toList();
        String header = lines.stream().filter(line -> line.startsWith("ID")).findFirst().orElseThrow();
        String shortRow = lines.stream().filter(line -> line.startsWith("APP-1 ")).findFirst().orElseThrow();
        String longRow = lines.stream().filter(line -> line.startsWith("APP-2026-002")).findFirst().orElseThrow();

        assertEquals(header.indexOf("STATUS"), shortRow.indexOf("DRAFT"));
        assertEquals(header.indexOf("STATUS"), longRow.indexOf("DRAFT"));
    }

    @Test
    @DisplayName("the summary lists every status, including the empty ones")
    void summaryListsEveryStatus() {
        String output = render();

        for (JobApplicationStatus status : JobApplicationStatus.values()) {
            assertTrue(output.contains(status.name()), "missing " + status + " in:\n" + output);
        }
    }

    @Test
    @DisplayName("rates are rounded to whole percents and ignore drafts")
    void ratesAreWholePercentsAndIgnoreDrafts() {
        service.register(new JobApplicationId("APP-1"), "Kaspi", "Java Intern");   // never sent
        service.register(new JobApplicationId("APP-2"), "Halyk", "QA Engineer");
        service.register(new JobApplicationId("APP-3"), "Jusan", "Analyst");
        service.register(new JobApplicationId("APP-4"), "Freedom", "Developer");
        service.submit(new JobApplicationId("APP-2"));
        service.changeStatus(new JobApplicationId("APP-2"), JobApplicationStatus.INTERVIEWING);
        service.changeStatus(new JobApplicationId("APP-2"), JobApplicationStatus.OFFER);
        service.submit(new JobApplicationId("APP-3"));
        service.submit(new JobApplicationId("APP-4"));

        String output = render();

        assertTrue(output.contains("submitted     3"), output);
        assertTrue(output.contains("offer rate    33%"), output);
    }

    @Test
    @DisplayName("the demo runs end to end without blowing up")
    void demoRunsEndToEnd() {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            JobTrackerDemo.main(new String[0]);
        } finally {
            System.setOut(original);
        }

        String output = captured.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("JOB APPLICATION TRACKER"), output);
        assertTrue(output.contains("Cannot move a job application from REJECTED to OFFER"), output);
        assertTrue(output.contains("Cannot move a job application from DRAFT to OFFER"), output);
        assertTrue(output.contains("DRAFT -> APPLIED"), output);
    }
}
