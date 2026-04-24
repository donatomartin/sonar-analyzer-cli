package com.donatomartin.sonarcli.analyzers.java.runtime;

import com.donatomartin.sonarcli.core.runtime.AnalysisParameters;
import com.donatomartin.sonarcli.core.scan.ProjectScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaAnalyzerRuntimeDoctorTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldExplainHowToFixMissingBinariesAndLibraries() throws IOException {
    Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    Files.createDirectories(tempDir.resolve("src/main/java/demo"));
    Files.createDirectories(tempDir.resolve("src/test/java/demo"));
    Files.writeString(tempDir.resolve("src/main/java/demo/App.java"), "package demo; class App {}");
    Files.writeString(tempDir.resolve("src/test/java/demo/AppTest.java"), "package demo; class AppTest {}");

    var project = new ProjectScanner().scan(tempDir, List.of(), List.of(), List.of(), List.of());
    var parameters = new AnalysisParameters(
      tempDir,
      List.of("java"),
      List.of(),
      List.of(),
      List.of(),
      List.of(),
      1_024_000L,
      20_000,
      List.of(),
      List.of(),
      null,
      false,
      false,
      List.of(),
      List.of(),
      List.of(),
      List.of(),
      null,
      false,
      null
    );

    var lines = new JavaAnalyzerRuntime().doctor(project, parameters);

    assertTrue(lines.stream().anyMatch(line -> line.contains("No compiled main classes were resolved")));
    assertTrue(lines.stream().anyMatch(line -> line.contains("No dependency jars were resolved for sonar.java.libraries")));
    assertTrue(lines.stream().anyMatch(line -> line.contains("mvn -q -DskipTests compile test-compile")));
  }
}
