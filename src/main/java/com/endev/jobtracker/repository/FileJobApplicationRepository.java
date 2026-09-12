package com.endev.jobtracker.repository;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps job applications in a plain text file, one application per line, so the
 * tracker still knows about them the next time it starts.
 *
 * <p>A company can take weeks to answer, which is exactly the stretch of time an
 * in-memory tracker forgets everything. The file is written in full after every
 * change: the data is small, and rewriting it keeps the file consistent even if
 * the program stops right afterwards.
 *
 * <p>Only the current state is stored — id, company, position and status. The
 * status history lives in memory for the run that produced it, the same as for
 * an application rebuilt with {@link JobApplication#at}.
 */
public final class FileJobApplicationRepository implements JobApplicationRepository {

    private static final String SEPARATOR = "\t";
    private static final int FIELD_COUNT = 4;

    private final Path file;
    private final InMemoryJobApplicationRepository loaded = new InMemoryJobApplicationRepository();

    /**
     * Opens the tracker's storage, reading whatever is already in {@code file}.
     * A file that does not exist yet simply means there is nothing stored.
     *
     * @throws StorageException if the file exists but cannot be read or understood
     */
    public FileJobApplicationRepository(Path file) {
        this.file = Objects.requireNonNull(file, "File is required");
        load();
    }

    @Override
    public void save(JobApplication application) {
        loaded.save(application);
        flush();
    }

    @Override
    public Optional<JobApplication> findById(JobApplicationId id) {
        return loaded.findById(id);
    }

    @Override
    public List<JobApplication> findAll() {
        return loaded.findAll();
    }

    @Override
    public List<JobApplication> findByStatus(JobApplicationStatus status) {
        return loaded.findByStatus(status);
    }

    @Override
    public boolean existsById(JobApplicationId id) {
        return loaded.existsById(id);
    }

    @Override
    public boolean deleteById(JobApplicationId id) {
        boolean removed = loaded.deleteById(id);
        if (removed) {
            flush();
        }
        return removed;
    }

    @Override
    public long count() {
        return loaded.count();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new StorageException("Cannot read " + file, failure);
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.isBlank()) {
                loaded.save(parse(line, index + 1));
            }
        }
    }

    private JobApplication parse(String line, int lineNumber) {
        String[] fields = line.split(SEPARATOR, -1);
        if (fields.length != FIELD_COUNT) {
            throw new StorageException("%s line %d: expected %d fields separated by tabs, found %d"
                    .formatted(file, lineNumber, FIELD_COUNT, fields.length));
        }
        try {
            return JobApplication.at(
                    new JobApplicationId(fields[0]),
                    fields[1],
                    fields[2],
                    JobApplicationStatus.valueOf(fields[3]));
        } catch (IllegalArgumentException invalid) {
            throw new StorageException("%s line %d: %s".formatted(file, lineNumber, invalid.getMessage()),
                    invalid);
        }
    }

    private void flush() {
        List<String> lines = new ArrayList<>();
        for (JobApplication application : loaded.findAll()) {
            lines.add(format(application));
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new StorageException("Cannot write " + file, failure);
        }
    }

    private String format(JobApplication application) {
        return String.join(SEPARATOR,
                requireStorable(application.id().toString(), "Id"),
                requireStorable(application.company(), "Company"),
                requireStorable(application.position(), "Position"),
                application.status().name());
    }

    /**
     * A tab or a line break inside a value would split one application across
     * fields or lines and quietly corrupt the file, so it is refused up front.
     */
    private String requireStorable(String value, String field) {
        if (value.contains(SEPARATOR) || value.contains("\n") || value.contains("\r")) {
            throw new StorageException(field + " must not contain a tab or a line break: \"" + value + "\"");
        }
        return value;
    }
}
