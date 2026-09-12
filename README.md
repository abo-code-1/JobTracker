# Job Application Tracker

## Product

A tracker for **job applications**. A student or job seeker applies to many companies at
once and quickly loses track of where each application stands: which ones were never sent,
which are waiting on a recruiter, which reached an interview loop, and which are already
closed. This product keeps one record per application and enforces the stages it may move
through, so the stored pipeline reflects what actually happened.

Run the tests with `mvn -q test`.

## Core item

The core item is a **job application**: one candidate applying to one opening at one company.

It is identified by `JobApplicationId` (for example `APP-2026-014`), which refuses a `null`
or blank value, and it carries exactly one `JobApplicationStatus` at a time:

- `DRAFT` — written up, but not sent to the company yet
- `APPLIED` — submitted and awaiting a response
- `INTERVIEWING` — the company invited the candidate; the interview loop is running
- `OFFER` — the company extended a written offer
- `ACCEPTED` — the candidate signed the offer (final)
- `REJECTED` — the company turned the candidate down (final)
- `WITHDRAWN` — the candidate pulled out of the process (final)

`JobApplicationPolicy.move(from, to)` returns the new status when the change is allowed and
throws `IllegalStateException` when it is forbidden.

## Status table

| From | To | Allowed? | Reason |
| --- | --- | --- | --- |
| `APPLIED` | `INTERVIEWING` | Allowed | The recruiter answered and invited the candidate, so the application moves to the next stage of the funnel |
| `INTERVIEWING` | `OFFER` | Allowed | The interview loop finished successfully and the company extended a written offer |
| `REJECTED` | `OFFER` | Forbidden | A rejection closes the process; reopening it in place would erase the rejection from the history |
| `DRAFT` | `OFFER` | Forbidden | An application that was never submitted cannot produce an offer; the submission step must not be skipped |

## Forbidden — why

**`REJECTED` → `OFFER`.** A rejection is a final outcome recorded against a specific opening.
If a closed application could be flipped back into an offer, the rejection would silently
disappear from the record, and the tracker would no longer show that the company said no —
the one fact a job seeker most needs when deciding whether to apply there again. When a
company reconsiders a candidate, that is a new conversation about a new opening, so it
belongs in a new application with its own id and its own history. Keeping the transition
forbidden is what makes "how many rejections did I get this month?" an answerable question.

**`DRAFT` → `OFFER`.** A draft has not been sent to anybody. Nothing has happened on the
company's side yet, so an offer cannot exist. Allowing the jump would let a mistyped status
invent an offer that was never made, and it would corrupt every funnel number derived from
the tracker: applications sent, response rate, and interview-to-offer conversion would all
be computed from a stage that was never actually reached. Forcing the application through
`APPLIED` first keeps the stored pipeline equal to the real one.
