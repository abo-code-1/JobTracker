package com.endev.jobtracker.cli;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.service.PipelineReport;

import java.util.List;

/**
 * Turns the tracker's state into the text shown on the console.
 *
 * <p>Formatting lives here rather than inside the demo so it can be asserted on
 * in a test instead of being eyeballed in a terminal.
 */
public final class PipelinePrinter {

    private static final String TITLE = "JOB APPLICATION TRACKER";

    /**
     * Renders the applications as a table followed by the pipeline summary.
     *
     * @return the report text, ending in a newline
     */
    public String render(List<JobApplication> applications, PipelineReport report) {
        StringBuilder out = new StringBuilder();
        out.append(TITLE).append(System.lineSeparator());
        out.append("=".repeat(TITLE.length())).append(System.lineSeparator());
        out.append(System.lineSeparator());
        appendApplications(out, applications);
        out.append(System.lineSeparator());
        appendSummary(out, report);
        return out.toString();
    }

    private void appendApplications(StringBuilder out, List<JobApplication> applications) {
        if (applications.isEmpty()) {
            out.append("No applications yet.").append(System.lineSeparator());
            return;
        }

        int idWidth = widthOf(applications, application -> application.id().toString(), "ID");
        int companyWidth = widthOf(applications, JobApplication::company, "COMPANY");
        int positionWidth = widthOf(applications, JobApplication::position, "POSITION");

        appendRow(out, idWidth, companyWidth, positionWidth, "ID", "COMPANY", "POSITION", "STATUS");
        for (JobApplication application : applications) {
            appendRow(out, idWidth, companyWidth, positionWidth,
                    application.id().toString(),
                    application.company(),
                    application.position(),
                    application.status().name());
        }
    }

    private void appendSummary(StringBuilder out, PipelineReport report) {
        out.append("PIPELINE").append(System.lineSeparator());
        for (JobApplicationStatus status : JobApplicationStatus.values()) {
            out.append("  ")
                    .append(pad(status.name(), 14))
                    .append(report.countOf(status))
                    .append(System.lineSeparator());
        }
        out.append(System.lineSeparator());
        out.append("  ").append(pad("total", 14)).append(report.total()).append(System.lineSeparator());
        out.append("  ").append(pad("submitted", 14)).append(report.submitted()).append(System.lineSeparator());
        out.append("  ").append(pad("open", 14)).append(report.open()).append(System.lineSeparator());
        out.append("  ").append(pad("offer rate", 14)).append(percent(report.offerRate()))
                .append(System.lineSeparator());
        out.append("  ").append(pad("rejection", 14)).append(percent(report.rejectionRate()))
                .append(System.lineSeparator());
    }

    private void appendRow(StringBuilder out, int idWidth, int companyWidth, int positionWidth,
                           String id, String company, String position, String status) {
        out.append(pad(id, idWidth + 2))
                .append(pad(company, companyWidth + 2))
                .append(pad(position, positionWidth + 2))
                .append(status)
                .append(System.lineSeparator());
    }

    private static int widthOf(List<JobApplication> applications,
                               java.util.function.Function<JobApplication, String> field,
                               String header) {
        int width = header.length();
        for (JobApplication application : applications) {
            width = Math.max(width, field.apply(application).length());
        }
        return width;
    }

    private static String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    /** Rounded whole percent, formatted without depending on the machine's locale. */
    private static String percent(double rate) {
        return Math.round(rate * 100) + "%";
    }
}
