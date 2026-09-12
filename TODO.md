# Job Application Tracker — team board

Two of us work on this repo. This file is the single place that says **what is
done, who is on what, and what comes next**. Update it in the same commit as the
code it describes, so the board can never drift from the repository.

> Owners are written as **A** (Abror) and **B** (teammate). Rename them to the
> real names once you agree who is who.

---

## How we work

1. **Claim before you code.** Change your task's status to `WIP` and put your
   initial in *Owner*, commit that one-line change, push it. Now the other
   person can see the task is taken.
2. **One task, one commit.** Each task below is sized to be a single focused
   commit, so `git log` reads as the history of this board.
3. **Green before push.** `mvn -q test` must pass. A red main blocks the other
   person, and unblocking someone else's failure wastes an evening.
4. **Rebase, never merge.** `git pull --rebase origin main` before pushing. The
   history stays linear and easy to read at grading time.
5. **Stay off each other's files.** Every task lists the files it touches. Two
   people editing the same file on the same day is the one thing that turns a
   two-person project into a merge-conflict project.
6. **Never force-push `main`.** Rewriting shared history throws away work the
   other person may already have pulled.

### Commit message format

```
Short imperative subject, under ~72 characters

Why the change was needed and what it makes possible. Not a list of the
files touched: the diff already says that.
```

### Hard constraints (assignment rules — do not break)

- Plain **Java 21 + Maven + JUnit 5**. No Spring, no database, no new
  dependencies outside the test scope.
- `README.md` is graded. It must keep exactly **4 headings** and exactly **one
  table with 4 data rows**. Do not edit it without both of us agreeing.
- The three graded types keep their names: `JobApplicationId`,
  `JobApplicationStatus`, `JobApplicationPolicy`. `move(from, to)` returns the
  new status or throws `IllegalStateException` — never a boolean.
- Every new behaviour ships with tests.

### Status legend

| Mark | Meaning |
| --- | --- |
| `TODO` | Nobody has started it |
| `WIP` | Claimed, in progress — check the owner before touching it |
| `REVIEW` | Pushed, waiting for the other person to read the diff |
| `DONE` | Merged into `main`, tests green |

---

## Where we are

**Done: 6 commits · 58 tests green · console demo runs.**
**Next up: T-06 and T-07 — they touch different packages, so both can start at once.**

---

## Board

| ID | Task | Owner | Status | Commit |
| --- | --- | --- | --- | --- |
| T-01 | Domain rules: id, status enum, policy, README | A | `DONE` | `9d6a2bd` |
| T-02 | `JobApplication` aggregate that owns its status | A | `DONE` | `dc510ac` |
| T-03 | Repository interface + in-memory implementation | A | `DONE` | `7c7e534` |
| T-04 | Status change history with an injectable `Clock` | A | `DONE` | `09d043a` |
| T-05 | Service layer + `PipelineReport` | A | `DONE` | `63b4a5b` |
| T-06 | Console demo + `PipelinePrinter` | A | `DONE` | `02761f1` |
| T-07 | Save and load applications from a file | — | `TODO` | |
| T-08 | Search, filter and sort applications | — | `TODO` | |
| T-09 | Interactive console menu | — | `TODO` | |
| T-10 | Find stale applications waiting too long | — | `TODO` | |
| T-11 | Per-company statistics | — | `TODO` | |

---

## T-07 · Save and load applications from a file

**Suggested owner:** A · **Depends on:** nothing · **Blocks:** T-09

Everything disappears when the program exits, which makes the tracker useless
for its actual purpose: watching an application over the weeks it takes a
company to answer.

- [ ] `FileJobApplicationRepository implements JobApplicationRepository`, writing
      one line per application to a plain text file
- [ ] Load an existing file on construction; an absent file means an empty
      tracker, not a crash
- [ ] A malformed line names the file and the line number in the message rather
      than throwing a bare `NumberFormatException`
- [ ] Round-trip test: save applications, load them into a new instance, get the
      same data back
- [ ] Tests use JUnit's `@TempDir` — never write into the project directory

**Files:** `src/main/java/com/endev/jobtracker/repository/FileJobApplicationRepository.java`,
matching test. Do not change `JobApplicationRepository` itself without telling B.

---

## T-08 · Search, filter and sort applications

**Suggested owner:** B · **Depends on:** nothing · **Blocks:** nothing

With twenty applications, `findAll()` is a wall of text. We need to ask real
questions: everything at Kaspi, everything still open, oldest first.

- [ ] `ApplicationQuery` holding optional filters: company, position keyword,
      set of statuses, "registered after" instant
- [ ] Case-insensitive, partial matching on company and position
- [ ] Sorting by registration date and by company name
- [ ] `JobApplicationService.search(ApplicationQuery)` returning the matches
- [ ] Tests for each filter alone, two filters combined, and a query that
      matches nothing (empty list, never `null`)

**Files:** `src/main/java/com/endev/jobtracker/service/ApplicationQuery.java`,
one new method on `JobApplicationService`, matching test.

---

## T-09 · Interactive console menu

**Suggested owner:** A · **Depends on:** T-07 · **Blocks:** nothing

`JobTrackerDemo` runs a fixed script. To actually use the tracker, a person has
to be able to type at it.

- [ ] Commands: `add`, `list`, `move`, `report`, `history`, `help`, `quit`
- [ ] Parsing lives in its own class that turns a line of text into a command
      object, so it can be tested without reading `System.in`
- [ ] An unknown command or a bad argument prints the problem and re-prompts;
      it never exits or throws
- [ ] A forbidden status change prints the policy's message and keeps going
- [ ] Tests cover the parser directly, plus one loop test driven by a
      `StringReader` of scripted input

**Files:** `src/main/java/com/endev/jobtracker/cli/` (new classes only — leave
`PipelinePrinter` alone), matching tests.

---

## T-10 · Find stale applications waiting too long

**Suggested owner:** B · **Depends on:** nothing · **Blocks:** nothing

The tracker knows when each status change happened but never uses it. The most
useful thing it could tell a job seeker is which companies have gone quiet.

- [ ] `StaleApplicationFinder` listing `APPLIED` applications whose last change
      is older than a given number of days
- [ ] Days elapsed computed from the injected `Clock`, never `Instant.now()`
- [ ] Closed applications are never stale, however old they are
- [ ] Tests use a fixed clock and check the boundary exactly: at 13 days not
      stale, at 14 days stale

**Files:** `src/main/java/com/endev/jobtracker/service/StaleApplicationFinder.java`,
matching test.

---

## T-11 · Per-company statistics

**Suggested owner:** either, once T-08 and T-10 are in

`PipelineReport` covers the whole pipeline but cannot say which companies are
worth the effort.

- [ ] Applications grouped by company, with a per-company status breakdown
- [ ] Average days from `APPLIED` to the first reply, computed from history
- [ ] Companies that never replied are reported as such, not silently counted
      as an average of zero
- [ ] `PipelinePrinter` renders the breakdown under the existing summary
- [ ] Tests for a company with several applications, and for one that never
      replied

**Files:** `src/main/java/com/endev/jobtracker/service/CompanyReport.java`, an
addition to `PipelinePrinter`, matching tests. Coordinate with whoever last
touched `PipelinePrinter`.
