package com.endev.jobtracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobApplicationPolicyTest {

    private final JobApplicationPolicy policy = new JobApplicationPolicy();

    /**
     * The four rows of the status table in README.md: two allowed changes and
     * two forbidden ones.
     */
    @ParameterizedTest(name = "[{index}] {0} -> {1} allowed={2}")
    @CsvSource({
            "APPLIED,      INTERVIEWING, true",
            "INTERVIEWING, OFFER,        true",
            "REJECTED,     OFFER,        false",
            "DRAFT,        OFFER,        false"
    })
    @DisplayName("the status table from the README is enforced")
    void statusTableFromReadme(JobApplicationStatus from, JobApplicationStatus to, boolean allowed) {
        if (allowed) {
            assertEquals(to, policy.move(from, to));
        } else {
            assertThrows(IllegalStateException.class, () -> policy.move(from, to));
        }
    }

    @ParameterizedTest(name = "[{index}] id=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("a null or blank id is rejected")
    void nullOrBlankIdIsRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> new JobApplicationId(value));
    }

    @Test
    @DisplayName("a valid id keeps its value, trimmed")
    void validIdIsKept() {
        assertEquals("APP-2026-014", new JobApplicationId("  APP-2026-014  ").value());
    }
}
