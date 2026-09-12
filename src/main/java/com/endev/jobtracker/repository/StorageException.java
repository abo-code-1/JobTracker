package com.endev.jobtracker.repository;

/**
 * Thrown when applications cannot be read from or written to their storage.
 *
 * <p>It is unchecked so that {@link JobApplicationRepository} stays free of
 * {@code IOException}: whether a repository happens to sit on a disk is its own
 * business, not something the service layer should have to handle.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
