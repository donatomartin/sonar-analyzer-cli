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

  // Preferred display order for issue types
  private static final List<String> TYPE_ORDER = List.of(
    "BUG", "VULNERABILITY", "SECURITY_HOTSPOT", "CODE_SMELL"
  );

  // Preferred display order for severities
  private static final List<String> SEVERITY_ORDER = List.of(
    "BLOCKER", "CRITICAL", "HIGH", "MAJOR", "MEDIUM", "MINOR", "LOW", "INFO"
  );

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
    var hasThresholds = (report.thresholds() != null && !report.thresholds().isEmpty())
      || (report.thresholdsBySeverity() != null && !report.thresholdsBySeverity().isEmpty());

    lines.add(tabs(colorEnabled, report, hasThresholds));
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

    lines.add("");
    renderGate(report, colorEnabled, lines);

    return String.join(System.lineSeparator(), lines);
  }

  private static void renderGate(AnalysisReport report, boolean colorEnabled, List<String> lines) {
    var thresholds = report.thresholds();
    var thresholdsBySeverity = report.thresholdsBySeverity();
    var hasTypeThresholds = thresholds != null && !thresholds.isEmpty();
    var hasSeverityThresholds = thresholdsBySeverity != null && !thresholdsBySeverity.isEmpty();
    var hasAnyThreshold = hasTypeThresholds || hasSeverityThresholds;

    // Tally issues by type and severity
    var byType = new LinkedHashMap<String, Integer>();
    var bySeverity = new LinkedHashMap<String, Integer>();
    for (IssueRecord issue : report.issues()) {
      if (issue.type() != null && !issue.type().isBlank()) {
        byType.merge(issue.type().toUpperCase(Locale.ROOT), 1, Integer::sum);
      }
      if (issue.severity() != null && !issue.severity().isBlank()) {
        bySeverity.merge(issue.severity().toUpperCase(Locale.ROOT), 1, Integer::sum);
      }
    }

    // Plain border strings (color applied separately — never use substring on ANSI strings)
    var borderPlain = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    var innerDividerPlain = "──────────────────────────────────────────────────────";
    var border = colorEnabled ? dim(true, borderPlain) : borderPlain;
    var innerDivider = "  " + (colorEnabled ? dim(true, innerDividerPlain) : innerDividerPlain);

    lines.add(border);
    lines.add("  " + (colorEnabled ? bold(hasAnyThreshold ? "Quality Gate" : "Issue Breakdown") : (hasAnyThreshold ? "Quality Gate" : "Issue Breakdown")));
    lines.add(border);

    if (byType.isEmpty() && bySeverity.isEmpty() && !hasAnyThreshold) {
      lines.add("  " + (colorEnabled ? green("✓  No issues found") : "No issues found"));
      lines.add(border);
      return;
    }

    int totalFailed = 0;

    // ── By Type section ──────────────────────────────────────────
    var displayTypes = orderedKeys(byType, hasTypeThresholds ? thresholds : Map.of(), TYPE_ORDER);
    if (!displayTypes.isEmpty() || hasTypeThresholds) {
      var subheading = "  " + (colorEnabled ? dim(true, "By Type") : "By Type");
      lines.add(subheading);
      if (hasTypeThresholds) {
        var header = "  " + padRight("TYPE", 18) + "  " + padLeft("FOUND", 6) + "  " + padLeft("MAX", 6) + "  " + padRight("BAR", 10) + "  STATUS";
        lines.add(colorEnabled ? dim(true, header) : header);
      } else {
        var header = "  " + padRight("TYPE", 18) + "  " + padLeft("FOUND", 6);
        lines.add(colorEnabled ? dim(true, header) : header);
      }
      lines.add(innerDivider);
      for (String type : displayTypes) {
        var count = byType.getOrDefault(type, 0);
        if (hasTypeThresholds) {
          var allowed = thresholds.getOrDefault(type, -1);
          var exceeded = allowed >= 0 && count > allowed;
          if (exceeded) totalFailed++;
          lines.add(renderThresholdRow(type, count, allowed, exceeded, colorEnabled));
        } else {
          lines.add(renderSimpleRow(type, count, colorEnabled, isCriticalType(type)));
        }
      }
      lines.add(innerDivider);
    }

    // ── By Severity section ───────────────────────────────────────
    var displaySeverities = orderedKeys(bySeverity, hasSeverityThresholds ? thresholdsBySeverity : Map.of(), SEVERITY_ORDER);
    if (!displaySeverities.isEmpty() || hasSeverityThresholds) {
      if (!displayTypes.isEmpty() || hasTypeThresholds) {
        lines.add(""); // spacing between subsections
      }
      var subheading = "  " + (colorEnabled ? dim(true, "By Severity") : "By Severity");
      lines.add(subheading);
      if (hasSeverityThresholds) {
        var header = "  " + padRight("SEVERITY", 18) + "  " + padLeft("FOUND", 6) + "  " + padLeft("MAX", 6) + "  " + padRight("BAR", 10) + "  STATUS";
        lines.add(colorEnabled ? dim(true, header) : header);
      } else {
        var header = "  " + padRight("SEVERITY", 18) + "  " + padLeft("FOUND", 6);
        lines.add(colorEnabled ? dim(true, header) : header);
      }
      lines.add(innerDivider);
      for (String severity : displaySeverities) {
        var count = bySeverity.getOrDefault(severity, 0);
        if (hasSeverityThresholds) {
          var allowed = thresholdsBySeverity.getOrDefault(severity, -1);
          var exceeded = allowed >= 0 && count > allowed;
          if (exceeded) totalFailed++;
          lines.add(renderThresholdRow(severity, count, allowed, exceeded, colorEnabled));
        } else {
          lines.add(renderSimpleRow(severity, count, colorEnabled, isCriticalSeverity(severity)));
        }
      }
      lines.add(innerDivider);
    }

    // ── Result footer ─────────────────────────────────────────────
    if (hasAnyThreshold) {
      int total = byType.values().stream().mapToInt(Integer::intValue).sum();
      if (totalFailed == 0) {
        lines.add("  Result: " + (colorEnabled ? green("✓  PASSED") : "✓  PASSED")
          + "  ·  " + total + " issue" + (total == 1 ? "" : "s") + ", all within limits");
      } else {
        lines.add("  Result: " + (colorEnabled ? red("✗  FAILED") : "✗  FAILED")
          + "  ·  " + totalFailed + " threshold" + (totalFailed == 1 ? "" : "s") + " exceeded");
      }
    } else {
      int total = byType.values().stream().mapToInt(Integer::intValue).sum();
      if (total == 0) {
        lines.add("  " + (colorEnabled ? green("No issues") : "No issues"));
      } else {
        lines.add("  " + (colorEnabled ? yellow(total + " issue" + (total == 1 ? "" : "s") + " total") : total + " issue" + (total == 1 ? "" : "s") + " total"));
      }
    }

    lines.add(border);
  }

  /** Returns keys in preferred order, appending any extras not in the order list. */
  private static List<String> orderedKeys(Map<String, Integer> counts, Map<String, Integer> thresholds, List<String> preferredOrder) {
    var result = new ArrayList<String>();
    for (String key : preferredOrder) {
      if (counts.containsKey(key) || thresholds.containsKey(key)) {
        result.add(key);
      }
    }
    for (String key : counts.keySet()) {
      if (!result.contains(key)) result.add(key);
    }
    for (String key : thresholds.keySet()) {
      if (!result.contains(key)) result.add(key);
    }
    return result;
  }

  private static String renderThresholdRow(String type, int count, int allowed, boolean exceeded, boolean colorEnabled) {
    // Pad all values with plain strings FIRST, then apply color
    var typePadded = padRight(type, 18);
    var countPadded = padLeft(String.valueOf(count), 6);
    var allowedPadded = allowed < 0 ? padLeft("—", 6) : padLeft(String.valueOf(allowed), 6);
    var bar = renderBar(count, allowed, colorEnabled);
    var statusPlain = exceeded ? "✗ FAIL" : "✓ ok";

    if (!colorEnabled) {
      return "  " + typePadded + "  " + countPadded + "  " + allowedPadded + "  " + bar + "  " + statusPlain;
    }
    var typeStr = exceeded ? bold(typePadded) : typePadded;
    var countStr = exceeded ? red(countPadded) : (count == 0 ? dim(true, countPadded) : countPadded);
    var status = exceeded ? red(statusPlain) : green(statusPlain);
    return "  " + typeStr + "  " + countStr + "  " + allowedPadded + "  " + bar + "  " + status;
  }

  private static String renderSimpleRow(String label, int count, boolean colorEnabled, boolean critical) {
    var labelPadded = padRight(label, 18);
    var countPadded = padLeft(String.valueOf(count), 6);
    if (!colorEnabled) {
      return "  " + labelPadded + "  " + countPadded;
    }
    var countStr = count == 0
      ? dim(true, countPadded)
      : (critical ? red(countPadded) : yellow(countPadded));
    return "  " + labelPadded + "  " + countStr;
  }

  private static String renderBar(int count, int allowed, boolean colorEnabled) {
    if (allowed < 0) {
      // No limit — return plain dashes (10 chars, no color so padRight works correctly)
      return "──────────";
    }
    int bars = 10;
    int filled;
    if (allowed == 0) {
      filled = count > 0 ? bars : 0;
    } else {
      filled = Math.min(bars, (int) Math.ceil((double) count / allowed * bars));
    }
    var exceeded = count > allowed;
    var sb = new StringBuilder();
    for (int i = 0; i < bars; i++) {
      sb.append(i < filled ? "█" : "░");
    }
    var bar = sb.toString(); // always 10 chars — apply color AFTER so padRight(bar,10) works
    if (!colorEnabled) {
      return bar;
    }
    return exceeded ? red(bar) : (filled > bars * 0.7 ? yellow(bar) : green(bar));
  }

  private static boolean isCriticalType(String type) {
    return "BUG".equals(type) || "VULNERABILITY".equals(type) || "SECURITY_HOTSPOT".equals(type);
  }

  private static boolean isCriticalSeverity(String severity) {
    return "BLOCKER".equals(severity) || "CRITICAL".equals(severity) || "HIGH".equals(severity);
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

  private static String tabs(boolean colorEnabled, AnalysisReport report, boolean hasThresholds) {
    var tabNames = new ArrayList<>(List.of("Summary", "Files", "Rules", "Warnings"));
    if (hasThresholds) {
      tabNames.add("Gate");
    }
    var rendered = new ArrayList<String>();
    for (String tab : tabNames) {
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
