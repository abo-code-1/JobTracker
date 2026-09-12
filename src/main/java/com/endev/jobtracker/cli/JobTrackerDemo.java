package com.endev.jobtracker.cli;

import com.endev.jobtracker.JobApplicationId;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.repository.InMemoryJobApplicationRepository;
import com.endev.jobtracker.service.JobApplicationService;

/**
 * Runs a short, realistic session against the tracker and prints the result.
 *
 * <p>Run it with:
 * <pre>mvn -q compile exec:java -Dexec.mainClass=com.endev.jobtracker.cli.JobTrackerDemo</pre>
 * or straight from the IDE.
 */
public final class JobTrackerDemo {

    private JobTrackerDemo() {
    }

    public static void main(String[] args) {
        JobApplicationService service = new JobApplicationService(new InMemoryJobApplicationRepository());

        JobApplicationId kaspi = new JobApplicationId("APP-2026-001");
        JobApplicationId halyk = new JobApplicationId("APP-2026-002");
        JobApplicationId freedom = new JobApplicationId("APP-2026-003");
        JobApplicationId jusan = new JobApplicationId("APP-2026-004");

        service.register(kaspi, "Kaspi", "Java Intern");
        service.register(halyk, "Halyk Bank", "Backend Engineer");
        service.register(freedom, "Freedom Finance", "QA Engineer");
        service.register(jusan, "Jusan", "Data Analyst");

        // Kaspi goes all the way to an offer.
        service.submit(kaspi);
        service.changeStatus(kaspi, JobApplicationStatus.INTERVIEWING);
        service.changeStatus(kaspi, JobApplicationStatus.OFFER);

        // Halyk turns the candidate down after the interview.
        service.submit(halyk);
        service.changeStatus(halyk, JobApplicationStatus.INTERVIEWING);
        service.changeStatus(halyk, JobApplicationStatus.REJECTED);

        // Freedom is still waiting for a reply, and Jusan never got sent.
        service.submit(freedom);

        System.out.print(new PipelinePrinter().render(service.findAll(), service.report()));

        System.out.println();
        System.out.println("The workflow refuses changes that could not have happened:");
        try {
            service.changeStatus(halyk, JobApplicationStatus.OFFER);
        } catch (IllegalStateException rejected) {
            System.out.println("  " + rejected.getMessage());
        }
        try {
            service.changeStatus(jusan, JobApplicationStatus.OFFER);
        } catch (IllegalStateException rejected) {
            System.out.println("  " + rejected.getMessage());
        }

        System.out.println();
        System.out.println("History of " + kaspi + ":");
        service.find(kaspi).history().forEach(change -> System.out.println("  " + change));
    }
}
