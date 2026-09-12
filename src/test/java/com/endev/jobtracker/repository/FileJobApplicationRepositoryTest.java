package com.endev.jobtracker.repository;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileJobApplicationRepositoryTest {

    @TempDir
    Path directory;

    private Path file;

    @BeforeEach
    void setUp() {
        file = directory.resolve("applications.tsv");
    }

    private static JobApplication application(String id, String company, JobApplicationStatus status) {
        return JobApplication.at(new JobApplicationId(id), company, "Java Intern", status);
    }

    private void writeLines(String... lines) throws IOException {
        Files.write(file, List.of(lines), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a file that does not exist yet means an empty tracker, not a crash")
    void missingFileMeansEmptyTracker() {
        JobApplicationRepository repository = new FileJobApplicationRepository(file);

        assertEquals(0, repository.count());
        assertFalse(Files.exists(file), "opening the tracker should not create the file on its own");
    }

    @Test
    @DisplayName("applications survive being written and read back")
    void applicationsSurviveARoundTrip() {
        JobApplicationRepository writer = new FileJobApplicationRepository(file);
        writer.save(application("APP-1", "Kaspi", JobApplicationStatus.APPLIED));
        writer.save(application("APP-2", "Halyk Bank", JobApplicationStatus.DRAFT));

        JobApplicationRepository reader = new FileJobApplicationRepository(file);

        assertEquals(2, reader.count());
        JobApplication first = reader.findById(new JobApplicationId("APP-1")).orElseThrow();
        assertEquals("Kaspi", first.company());
        assertEquals("Java Intern", first.position());
        assertEquals(JobApplicationStatus.APPLIED, first.status());
        assertEquals("Halyk Bank",
                reader.findById(new JobApplicationId("APP-2")).orElseThrow().company());
    }

    @Test
    @DisplayName("the saved order is the order that comes back")
    void savedOrderComesBack() {
        JobApplicationRepository writer = new FileJobApplicationRepository(file);
        writer.save(application("APP-3", "Freedom", JobApplicationStatus.DRAFT));
        writer.save(application("APP-1", "Kaspi", JobApplicationStatus.DRAFT));
        writer.save(application("APP-2", "Halyk", JobApplicationStatus.DRAFT));

        List<String> ids = new FileJobApplicationRepository(file).findAll().stream()
                .map(application -> application.id().toString())
                .toList();

        assertEquals(List.of("APP-3", "APP-1", "APP-2"), ids);
    }

    @Test
    @DisplayName("a status change is persisted, not just held in memory")
    void statusChangeIsPersisted() {
        JobApplicationRepository writer = new FileJobApplicationRepository(file);
        JobApplication application = application("APP-1", "Kaspi", JobApplicationStatus.DRAFT);
        writer.save(application);

        application.moveTo(JobApplicationStatus.APPLIED);
        writer.save(application);

        assertEquals(JobApplicationStatus.APPLIED,
                new FileJobApplicationRepository(file).findById(new JobApplicationId("APP-1"))
                        .orElseThrow().status());
    }

    @Test
    @DisplayName("a deletion is persisted")
    void deletionIsPersisted() {
        JobApplicationRepository writer = new FileJobApplicationRepository(file);
        writer.save(application("APP-1", "Kaspi", JobApplicationStatus.DRAFT));
        writer.save(application("APP-2", "Halyk", JobApplicationStatus.DRAFT));

        assertTrue(writer.deleteById(new JobApplicationId("APP-1")));

        JobApplicationRepository reader = new FileJobApplicationRepository(file);
        assertEquals(1, reader.count());
        assertFalse(reader.existsById(new JobApplicationId("APP-1")));
    }

    @Test
    @DisplayName("saving the same id twice leaves one line, not two")
    void savingSameIdTwiceLeavesOneLine() throws IOException {
        JobApplicationRepository writer = new FileJobApplicationRepository(file);
        writer.save(application("APP-1", "Kaspi", JobApplicationStatus.DRAFT));
        writer.save(application("APP-1", "Kaspi", JobApplicationStatus.APPLIED));

        assertEquals(1, Files.readAllLines(file, StandardCharsets.UTF_8).size());
        assertEquals(1, new FileJobApplicationRepository(file).count());
    }

    @Test
    @DisplayName("blank lines in the file are ignored")
    void blankLinesAreIgnored() throws IOException {
        writeLines("APP-1\tKaspi\tJava Intern\tAPPLIED", "", "   ",
                "APP-2\tHalyk\tQA Engineer\tDRAFT");

        assertEquals(2, new FileJobApplicationRepository(file).count());
    }

    @Test
    @DisplayName("a line with the wrong number of fields names the file and the line")
    void wrongFieldCountNamesTheLine() throws IOException {
        writeLines("APP-1\tKaspi\tJava Intern\tAPPLIED", "APP-2\tHalyk\tQA Engineer");

        StorageException failure = assertThrows(StorageException.class,
                () -> new FileJobApplicationRepository(file));

        assertTrue(failure.getMessage().contains("line 2"), failure.getMessage());
        assertTrue(failure.getMessage().contains(file.toString()), failure.getMessage());
        assertTrue(failure.getMessage().contains("found 3"), failure.getMessage());
    }

    @Test
    @DisplayName("an unknown status names the line instead of throwing a bare enum error")
    void unknownStatusNamesTheLine() throws IOException {
        writeLines("APP-1\tKaspi\tJava Intern\tGHOSTED");

        StorageException failure = assertThrows(StorageException.class,
                () -> new FileJobApplicationRepository(file));

        assertTrue(failure.getMessage().contains("line 1"), failure.getMessage());
        assertTrue(failure.getMessage().contains("GHOSTED"), failure.getMessage());
    }

    @Test
    @DisplayName("a blank id in the file names the line rather than producing a broken application")
    void blankIdNamesTheLine() throws IOException {
        writeLines("   \tKaspi\tJava Intern\tAPPLIED");

        StorageException failure = assertThrows(StorageException.class,
                () -> new FileJobApplicationRepository(file));

        assertTrue(failure.getMessage().contains("line 1"), failure.getMessage());
    }

    @Test
    @DisplayName("a value containing a tab is refused instead of corrupting the file")
    void valueContainingATabIsRefused() {
        JobApplicationRepository repository = new FileJobApplicationRepository(file);

        StorageException failure = assertThrows(StorageException.class,
                () -> repository.save(application("APP-1", "Kaspi\tBank", JobApplicationStatus.DRAFT)));

        assertTrue(failure.getMessage().contains("Company"), failure.getMessage());
    }

    @Test
    @DisplayName("the storage directory is created if it is missing")
    void storageDirectoryIsCreated() {
        Path nested = directory.resolve("data").resolve("applications.tsv");

        new FileJobApplicationRepository(nested).save(application("APP-1", "Kaspi", JobApplicationStatus.DRAFT));

        assertTrue(Files.exists(nested));
    }
}
