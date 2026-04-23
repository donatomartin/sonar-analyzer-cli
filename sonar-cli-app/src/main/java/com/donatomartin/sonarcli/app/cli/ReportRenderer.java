package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.core.model.AnalysisReport;
import com.donatomartin.sonarcli.core.model.IssueRecord;
import com.donatomartin.sonarcli.core.util.JsonSupport;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ReportRenderer {

  public enum Format {
    text,
    json
  }

  public void writeReport(AnalysisReport report, Format format, Path output) throws IOException {
    String rendered = format == Format.json ? JsonSupport.PRETTY_GSON.toJson(report) : renderText(report);
    if (output != null) {
      Files.writeString(output, rendered + System.lineSeparator());
      return;
    }
    System.out.println(rendered);
  }

  public void printRuleList(List<String> lines) {
    lines.forEach(System.out::println);
  }

  public void printDoctor(List<String> lines) {
    lines.forEach(System.out::println);
  }

  private String renderText(AnalysisReport report) {
    var lines = new ArrayList<String>();
    for (IssueRecord issue : report.issues()) {
      lines.add(
        issue.path()
          + ":"
          + issue.startLine()
          + ":"
          + Math.max(issue.startLineOffset(), 0)
          + "  "
          + pad(issue.selector(), 10)
          + "  "
          + pad(nullToDash(issue.severity()), 8)
          + "  "
          + pad(issue.analyzerId(), 5)
          + "  "
          + issue.message()
      );
    }
    if (lines.isEmpty()) {
      lines.add("No issues found.");
    }
    lines.add("");
    lines.add("Analyzers: " + String.join(", ", report.analyzers()));
    lines.add("Active rules: " + report.activeRules().size());
    lines.add("Files: " + report.stats().totalFiles());
    lines.add("Issues: " + report.stats().issues());
    lines.add("Parsing errors: " + report.stats().parsingErrors());
    lines.add("Duration: " + report.stats().durationMs() + " ms");
    return String.join(System.lineSeparator(), lines);
  }

  private static String pad(String value, int width) {
    return String.format("%-" + width + "s", value);
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }
}
