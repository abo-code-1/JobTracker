package com.endev.jobtracker.cli;

import com.endev.jobtracker.JobApplication;
import com.endev.jobtracker.JobApplicationStatus;
import com.endev.jobtracker.service.CompanyReport;
import com.endev.jobtracker.service.CompanyStats;
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

    /**
     * Renders the per-company breakdown, busiest company first.
     *
     * <p>A company that has never answered is shown as such rather than as a
     * reply time of zero, which would put the silent companies at the top of
     * the fastest list.
     */
    public String renderByCompany(CompanyReport report) {
        List<CompanyStats> rows = report.byCompany();
        StringBuilder out = new StringBuilder();
        out.append("BY COMPANY").append(System.lineSeparator());
        if (rows.isEmpty()) {
            out.append("  Nothing to break down yet.").append(System.lineSeparator());
            return out.toString();
        }

        int nameWidth = "COMPANY".length();
        for (CompanyStats row : rows) {
            nameWidth = Math.max(nameWidth, row.company().length());
        }

        appendCompanyRow(out, nameWidth, "COMPANY", "TOTAL", "OPEN", "OFFERS", "AVG REPLY");
        for (CompanyStats row : rows) {
            appendCompanyRow(out, nameWidth,
                    row.company(),
                    String.valueOf(row.total()),
                    String.valueOf(row.open()),
                    String.valueOf(row.offers()),
                    replyTime(row));
        }
        return out.toString();
    }

    private void appendCompanyRow(StringBuilder out, int nameWidth, String company,
                                  String total, String open, String offers, String reply) {
        out.append(pad(company, nameWidth + 2))
                .append(pad(total, 7))
                .append(pad(open, 6))
                .append(pad(offers, 8))
                .append(reply)
                .append(System.lineSeparator());
    }

    /** One decimal place, built without String.format so a comma locale cannot change it. */
    private static String replyTime(CompanyStats row) {
        if (!row.hasReplied()) {
            return "no reply yet";
        }
        double days = row.averageDaysToFirstReply().orElseThrow();
        return Math.round(days * 10) / 10.0 + " days";
    }
}
