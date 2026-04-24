package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.core.model.AnalysisReport;
import com.donatomartin.sonarcli.core.model.IssueRecord;
import com.donatomartin.sonarcli.core.model.ReportStats;
import com.donatomartin.sonarcli.core.model.RuleFamily;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRendererTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldRenderGroupedTextSections() throws IOException {
    var report = new AnalysisReport(
      "sonar-analyzer-cli/v2",
      "0.2.0-SNAPSHOT",
      tempDir.toString(),
      List.of("js"),
      Map.of("js", "11.7.1.36988"),
      List.of("typescript:S6564"),
      List.of("[js] Type-aware analysis is limited."),
      List.of(
        new IssueRecord("js", RuleFamily.JS, "S6564", "typescript:S6564", "src/example.ts", 1, 0, 1, 12, "Redundant type alias", "Major", "CODE_SMELL", List.of()),
        new IssueRecord("js", RuleFamily.JS, "S3776", "typescript:S3776", "src/example.ts", 3, 2, 3, 14, "Function is too complex", "Critical", "CODE_SMELL", List.of())
      ),
      new ReportStats(1, Map.of("js-ts", 1), 2, 0, 25)
    );

    Path output = tempDir.resolve("report.txt");
    new ReportRenderer().writeReport(report, ReportRenderer.Format.text, output);
    String rendered = Files.readString(output);

    assertTrue(rendered.contains("Summary"));
    assertTrue(rendered.contains("Files"));
    assertTrue(rendered.contains("Rules"));
    assertTrue(rendered.contains("Warnings"));
    assertTrue(rendered.contains("src/example.ts  (2 issues)"));
    assertTrue(rendered.contains("typescript:S6564"));
  }
}
