package com.endev.jobtracker;

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

class JobApplicationHistoryTest {

    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");

    /** A clock that advances one minute every time it is read, so each change gets its own timestamp. */
    private static final class SteppingClock extends Clock {
        private Instant now = START;

        @Override
        public Instant instant() {
            Instant current = now;
            now = now.plusSeconds(60);
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

    private static JobApplication draftAt(Clock clock) {
        return JobApplication.draft(new JobApplicationId("APP-2026-014"), "Kaspi", "Java Intern", clock);
    }

    @Test
    @DisplayName("a fresh application has no history yet")
    void freshApplicationHasNoHistory() {
        JobApplication application = draftAt(Clock.fixed(START, ZoneOffset.UTC));

        assertTrue(application.history().isEmpty());
        assertTrue(application.lastChangedAt().isEmpty());
        assertEquals(START, application.createdAt());
    }

    @Test
    @DisplayName("every accepted change is recorded in order with its timestamp")
    void acceptedChangesAreRecordedInOrder() {
        JobApplication application = draftAt(new SteppingClock());

        application.moveTo(JobApplicationStatus.APPLIED);
        application.moveTo(JobApplicationStatus.INTERVIEWING);
        application.moveTo(JobApplicationStatus.OFFER);

        List<StatusChange> history = application.history();

        assertEquals(3, history.size());
        assertEquals(new StatusChange(JobApplicationStatus.DRAFT, JobApplicationStatus.APPLIED,
                START.plusSeconds(60)), history.get(0));
        assertEquals(new StatusChange(JobApplicationStatus.APPLIED, JobApplicationStatus.INTERVIEWING,
                START.plusSeconds(120)), history.get(1));
        assertEquals(new StatusChange(JobApplicationStatus.INTERVIEWING, JobApplicationStatus.OFFER,
                START.plusSeconds(180)), history.get(2));
    }

    @Test
    @DisplayName("a rejected change is not recorded")
    void rejectedChangeIsNotRecorded() {
        JobApplication application = draftAt(new SteppingClock());

        assertThrows(IllegalStateException.class, () -> application.moveTo(JobApplicationStatus.OFFER));

        assertTrue(application.history().isEmpty());
        assertEquals(JobApplicationStatus.DRAFT, application.status());
    }

    @Test
    @DisplayName("lastChangedAt reports the most recent change")
    void lastChangedAtReportsMostRecentChange() {
        JobApplication application = draftAt(new SteppingClock());

        application.moveTo(JobApplicationStatus.APPLIED);
        application.moveTo(JobApplicationStatus.REJECTED);

        assertEquals(START.plusSeconds(120), application.lastChangedAt().orElseThrow());
    }

    @Test
    @DisplayName("the history handed out is a copy")
    void historyIsACopy() {
        JobApplication application = draftAt(new SteppingClock());
        application.moveTo(JobApplicationStatus.APPLIED);

        List<StatusChange> history = application.history();

        assertThrows(UnsupportedOperationException.class, () -> history.clear());
        assertEquals(1, application.history().size());
    }

    @Test
    @DisplayName("a status change rejects missing values")
    void statusChangeRejectsMissingValues() {
        assertThrows(NullPointerException.class,
                () -> new StatusChange(null, JobApplicationStatus.APPLIED, START));
        assertThrows(NullPointerException.class,
                () -> new StatusChange(JobApplicationStatus.DRAFT, null, START));
        assertThrows(NullPointerException.class,
                () -> new StatusChange(JobApplicationStatus.DRAFT, JobApplicationStatus.APPLIED, null));
    }
}
