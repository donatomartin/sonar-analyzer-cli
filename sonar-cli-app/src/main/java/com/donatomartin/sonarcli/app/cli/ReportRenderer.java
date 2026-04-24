package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.core.model.AnalysisReport;
import com.donatomartin.sonarcli.core.model.IssueRecord;
import com.donatomartin.sonarcli.core.util.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReportRenderer {

  public enum Format {
    text,
    json
  }

  public void writeReport(AnalysisReport report, Format format, Path output) throws IOException {
    boolean colorEnabled = output == null && useColor();
    String rendered = format == Format.json ? JsonSupport.PRETTY_GSON.toJson(report) : renderText(report, colorEnabled);
    if (output != null) {
      Files.writeString(output, rendered + System.lineSeparator());
      return;
    }
    System.out.println(rendered);
  }

  public void printRuleList(List<String> lines) {
    System.out.println(String.join(System.lineSeparator(), lines));
  }

  public void printDoctor(List<String> lines) {
    System.out.println(String.join(System.lineSeparator(), lines));
  }

  private String renderText(AnalysisReport report, boolean colorEnabled) {
    var lines = new ArrayList<String>();

    lines.add(tabs(colorEnabled, report));
    lines.add("");
    lines.add(section("Summary", colorEnabled));
    lines.add(
      String.format(
        Locale.ROOT,
        "Analyzers  %s",
        analyzerSummary(report)
      )
    );
    lines.add(
      String.format(
        Locale.ROOT,
        "Files      %d total  (%s)",
        report.stats().totalFiles(),
        fileSummary(report.stats().filesByKind())
      )
    );
    lines.add(
      String.format(
        Locale.ROOT,
        "Issues     %d  Warnings %d  Parsing %d  Active rules %d  Duration %d ms",
        report.stats().issues(),
        report.warnings().size(),
        report.stats().parsingErrors(),
        report.activeRules().size(),
        report.stats().durationMs()
      )
    );

    lines.add("");
    lines.add(section("Files", colorEnabled));
    if (report.issues().isEmpty()) {
      lines.add(colorEnabled ? green("No issues found.") : "No issues found.");
    } else {
      renderIssuesByFile(report.issues(), colorEnabled, lines);
    }

    lines.add("");
    lines.add(section("Rules", colorEnabled));
    if (report.issues().isEmpty()) {
      lines.add("No active issues.");
    } else {
      renderIssuesByRule(report.issues(), colorEnabled, lines);
    }

    lines.add("");
    lines.add(section("Warnings", colorEnabled));
    if (report.warnings().isEmpty()) {
      lines.add("No warnings.");
    } else {
      for (String warning : report.warnings()) {
        lines.add((colorEnabled ? yellow("! ") : "! ") + warning);
      }
    }

    return String.join(System.lineSeparator(), lines);
  }

  private static void renderIssuesByFile(List<IssueRecord> issues, boolean colorEnabled, List<String> lines) {
    var byFile = new LinkedHashMap<String, List<IssueRecord>>();
    for (IssueRecord issue : issues) {
      byFile.computeIfAbsent(issue.path(), ignored -> new ArrayList<>()).add(issue);
    }
    for (Map.Entry<String, List<IssueRecord>> entry : byFile.entrySet()) {
      lines.add(filePath(entry.getKey(), colorEnabled) + "  (" + entry.getValue().size() + " issues)");
      for (IssueRecord issue : entry.getValue()) {
        lines.add(
          "  "
            + padLeft(location(issue), 9)
            + "  "
            + severityBadge(issue.severity(), colorEnabled)
            + "  "
            + padRight(issue.selector(), 18)
            + "  "
            + issue.message()
        );
        for (var location : issue.secondaryLocations()) {
          lines.add(
            "             related  "
              + filePath(location.path(), colorEnabled)
              + ":"
              + location.startLine()
              + "  "
              + nullToDash(location.message())
          );
        }
      }
      lines.add("");
    }
    if (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
      lines.remove(lines.size() - 1);
    }
  }

  private static void renderIssuesByRule(List<IssueRecord> issues, boolean colorEnabled, List<String> lines) {
    var byRule = new LinkedHashMap<String, List<IssueRecord>>();
    for (IssueRecord issue : issues) {
      byRule.computeIfAbsent(issue.selector(), ignored -> new ArrayList<>()).add(issue);
    }
    for (Map.Entry<String, List<IssueRecord>> entry : byRule.entrySet()) {
      var first = entry.getValue().get(0);
      lines.add(
        padRight(entry.getKey(), 18)
          + "  "
          + severityBadge(first.severity(), colorEnabled)
          + "  "
          + entry.getValue().size()
          + " issues  "
          + first.message()
      );
    }
  }

  private static String tabs(boolean colorEnabled, AnalysisReport report) {
    var tabs = List.of("Summary", "Files", "Rules", "Warnings");
    var rendered = new ArrayList<String>();
    for (String tab : tabs) {
      rendered.add(colorEnabled ? cyan("[" + tab + "]") : "[" + tab + "]");
    }
    return String.join(" ", rendered) + "  " + dim(colorEnabled, report.engine() + " " + report.engineVersion());
  }

  private static String analyzerSummary(AnalysisReport report) {
    var analyzers = new ArrayList<String>();
    for (String analyzer : report.analyzers()) {
      var version = report.analyzerVersions().get(analyzer);
      analyzers.add(analyzer + (version == null ? "" : "@" + version));
    }
    return String.join(", ", analyzers);
  }

  private static String fileSummary(Map<String, Integer> filesByKind) {
    var parts = new ArrayList<String>();
    for (Map.Entry<String, Integer> entry : filesByKind.entrySet()) {
      if (entry.getValue() > 0) {
        parts.add(entry.getKey() + "=" + entry.getValue());
      }
    }
    return parts.isEmpty() ? "none" : String.join(", ", parts);
  }

  private static String section(String name, boolean colorEnabled) {
    return colorEnabled ? bold(name) : name;
  }

  private static String location(IssueRecord issue) {
    return issue.startLine() + ":" + Math.max(issue.startLineOffset(), 0);
  }

  private static String severityBadge(String severity, boolean colorEnabled) {
    var normalized = nullToDash(severity).toUpperCase(Locale.ROOT);
    if (!colorEnabled) {
      return "[" + normalized + "]";
    }
    return switch (normalized) {
      case "BLOCKER", "CRITICAL", "HIGH" -> red("[" + normalized + "]");
      case "MAJOR", "MEDIUM" -> yellow("[" + normalized + "]");
      case "MINOR", "LOW" -> blue("[" + normalized + "]");
      default -> dim(true, "[" + normalized + "]");
    };
  }

  private static String filePath(String path, boolean colorEnabled) {
    return colorEnabled ? green(path) : path;
  }

  private static String padLeft(String value, int width) {
    return String.format("%" + width + "s", value);
  }

  private static String padRight(String value, int width) {
    return String.format("%-" + width + "s", value);
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private static boolean useColor() {
    String term = System.getenv("TERM");
    return System.console() != null && term != null && !"dumb".equalsIgnoreCase(term);
  }

  private static String bold(String value) {
    return ansi("1", value);
  }

  private static String red(String value) {
    return ansi("31", value);
  }

  private static String green(String value) {
    return ansi("32", value);
  }

  private static String yellow(String value) {
    return ansi("33", value);
  }

  private static String blue(String value) {
    return ansi("34", value);
  }

  private static String cyan(String value) {
    return ansi("36", value);
  }

  private static String dim(boolean enabled, String value) {
    return enabled ? ansi("2", value) : value;
  }

  private static String ansi(String code, String value) {
    return "\u001B[" + code + "m" + value + "\u001B[0m";
  }
}
