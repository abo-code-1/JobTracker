package com.endev.jobtracker;

/**
 * The stages a job application moves through, from writing it up to a final outcome.
 */
public enum JobApplicationStatus {

    /** Written up, but not sent to the company yet. */
    DRAFT,

    /** Submitted to the company and awaiting a response. */
    APPLIED,

    /** The company invited the candidate and the interview loop is running. */
    INTERVIEWING,

    /** The company extended a written offer. */
    OFFER,

    /** The candidate signed the offer. Final. */
    ACCEPTED,

    /** The company turned the candidate down. Final. */
    REJECTED,

    /** The candidate pulled out of the process. Final. */
    WITHDRAWN
}
