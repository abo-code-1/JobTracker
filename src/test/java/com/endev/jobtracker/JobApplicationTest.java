package com.endev.jobtracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobApplicationTest {

    private static JobApplication draft() {
        return JobApplication.draft(new JobApplicationId("APP-2026-014"), "Kaspi", "Java Intern");
    }

    @Test
    @DisplayName("a new application starts as a draft")
    void newApplicationStartsAsDraft() {
        JobApplication application = draft();

        assertEquals(JobApplicationStatus.DRAFT, application.status());
        assertEquals("Kaspi", application.company());
        assertEquals("Java Intern", application.position());
    }

    @Test
    @DisplayName("an allowed change updates the status")
    void allowedChangeUpdatesStatus() {
        JobApplication application = draft();

        application.moveTo(JobApplicationStatus.APPLIED);
        application.moveTo(JobApplicationStatus.INTERVIEWING);

        assertEquals(JobApplicationStatus.INTERVIEWING, application.status());
    }

    @Test
    @DisplayName("a forbidden change is rejected and leaves the status untouched")
    void forbiddenChangeLeavesStatusUntouched() {
        JobApplication application = draft();

        assertThrows(IllegalStateException.class, () -> application.moveTo(JobApplicationStatus.OFFER));
        assertEquals(JobApplicationStatus.DRAFT, application.status());
    }

    @Test
    @DisplayName("canMoveTo agrees with the policy")
    void canMoveToAgreesWithPolicy() {
        JobApplication application = draft();

        assertTrue(application.canMoveTo(JobApplicationStatus.APPLIED));
        assertFalse(application.canMoveTo(JobApplicationStatus.OFFER));
        assertFalse(application.canMoveTo(null));
    }

    @ParameterizedTest(name = "{0} is final")
    @EnumSource(value = JobApplicationStatus.class,
            names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    @DisplayName("final statuses close the application")
    void finalStatusesCloseTheApplication(JobApplicationStatus status) {
        JobApplication application =
                JobApplication.at(new JobApplicationId("APP-1"), "Halyk", "Backend Engineer", status);

        assertTrue(application.isClosed());
    }

    @ParameterizedTest(name = "{0} is still open")
    @EnumSource(value = JobApplicationStatus.class,
            names = {"DRAFT", "APPLIED", "INTERVIEWING", "OFFER"})
    @DisplayName("open statuses can still move")
    void openStatusesCanStillMove(JobApplicationStatus status) {
        JobApplication application =
                JobApplication.at(new JobApplicationId("APP-1"), "Halyk", "Backend Engineer", status);

        assertFalse(application.isClosed());
    }

    @ParameterizedTest(name = "company=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("a null or blank company is rejected")
    void nullOrBlankCompanyIsRejected(String company) {
        JobApplicationId id = new JobApplicationId("APP-1");

        assertThrows(IllegalArgumentException.class,
                () -> JobApplication.draft(id, company, "Java Intern"));
    }

    @ParameterizedTest(name = "position=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("a null or blank position is rejected")
    void nullOrBlankPositionIsRejected(String position) {
        JobApplicationId id = new JobApplicationId("APP-1");

        assertThrows(IllegalArgumentException.class,
                () -> JobApplication.draft(id, "Kaspi", position));
    }

    @Test
    @DisplayName("applications are equal when their ids are equal")
    void applicationsAreEqualByIdOnly() {
        JobApplication one = JobApplication.draft(new JobApplicationId("APP-7"), "Kaspi", "Java Intern");
        JobApplication other = JobApplication.draft(new JobApplicationId("APP-7"), "Halyk", "QA Engineer");

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
    }
}
