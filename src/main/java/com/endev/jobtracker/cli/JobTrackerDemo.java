package com.endev.jobtracker.cli;

import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.InMemoryJobApplicationRepository;
import com.endev.jobtracker.repository.JobApplicationRepository;
import com.endev.jobtracker.service.CompanyReport;
import com.endev.jobtracker.service.JobApplicationService;
import com.endev.jobtracker.service.StaleApplicationFinder;
import com.endev.jobtracker.service.WaitingApplication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Runs a short, realistic session against the tracker and prints the result.
 *
 * <p>Run it with:
 * <pre>mvn -q compile exec:java -Dexec.mainClass=com.endev.jobtracker.cli.JobTrackerDemo</pre>
 * or straight from the IDE.
 *
 * <p>The demo drives a clock of its own instead of the system clock. A real
 * search plays out over weeks, and on the system clock every step would land in
 * the same microsecond, leaving every reply time reading "0.0 days".
 */
public final class JobTrackerDemo {

    /** A clock the demo advances by hand to play out six weeks in a few milliseconds. */
    private static final class DemoClock extends Clock {
        private Instant now = Instant.parse("2026-08-01T09:00:00Z");

        void advanceDays(long days) {
            now = now.plus(Duration.ofDays(days));
        }

        @Override
        public Instant instant() {
            return now;
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

    private JobTrackerDemo() {
    }

    public static void main(String[] args) {
        DemoClock clock = new DemoClock();
        JobApplicationRepository repository = new InMemoryJobApplicationRepository();
        JobApplicationService service = new JobApplicationService(repository, clock);
        StaleApplicationFinder staleFinder = new StaleApplicationFinder(repository, clock);

        JobApplicationId kaspi = new JobApplicationId("APP-2026-001");
        JobApplicationId halyk = new JobApplicationId("APP-2026-002");
        JobApplicationId freedom = new JobApplicationId("APP-2026-003");
        JobApplicationId jusan = new JobApplicationId("APP-2026-004");

        service.register(kaspi, "Kaspi", "Java Intern");
        service.register(halyk, "Halyk Bank", "Backend Engineer");
        service.register(freedom, "Freedom Finance", "QA Engineer");
        service.register(jusan, "Jusan", "Data Analyst");

        // Day 1: three of them go out. Jusan stays a draft.
        clock.advanceDays(1);
        service.submit(kaspi);
        service.submit(halyk);
        service.submit(freedom);

        // Day 5: two companies answer within four days. Freedom says nothing.
        clock.advanceDays(4);
        service.changeStatus(kaspi, JobApplicationStatus.INTERVIEWING);
        service.changeStatus(halyk, JobApplicationStatus.INTERVIEWING);

        // Day 11: Kaspi makes an offer.
        clock.advanceDays(6);
        service.changeStatus(kaspi, JobApplicationStatus.OFFER);

        // Day 14: Halyk turns the candidate down after the interview.
        clock.advanceDays(3);
        service.changeStatus(halyk, JobApplicationStatus.REJECTED);

        // Day 39: still nothing from Freedom.
        clock.advanceDays(25);

        PipelinePrinter printer = new PipelinePrinter();
        System.out.print(printer.render(service.findAll(), service.report()));
        System.out.println();
        System.out.print(printer.renderByCompany(CompanyReport.of(service.findAll())));

        System.out.println();
        System.out.println("GONE QUIET (no answer for two weeks or more)");
        var stale = staleFinder.findStalerThan(14);
        if (stale.isEmpty()) {
            System.out.println("  Nothing to chase.");
        } else {
            for (WaitingApplication waiting : stale) {
                System.out.println("  " + waiting);
            }
        }

        System.out.println();
        System.out.println("The workflow refuses changes that could not have happened:");
        printRefusal(service, halyk, JobApplicationStatus.OFFER);
        printRefusal(service, jusan, JobApplicationStatus.OFFER);

        System.out.println();
        System.out.println("History of " + kaspi + ":");
        service.find(kaspi).history().forEach(change -> System.out.println("  " + change));
    }

    private static void printRefusal(JobApplicationService service, JobApplicationId id,
                                     JobApplicationStatus target) {
        try {
            service.changeStatus(id, target);
        } catch (IllegalStateException refused) {
            System.out.println("  " + refused.getMessage());
        }
    }
}
