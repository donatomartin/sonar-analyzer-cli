package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.core.runtime.AnalysisParameters;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Option;

public class AnalysisOptionsMixin {

  @Option(names = "--format", defaultValue = "text", description = "Output format: ${COMPLETION-CANDIDATES}")
  ReportRenderer.Format format = ReportRenderer.Format.text;

  @Option(names = "--output", description = "Write output to a file")
  Path output;

  @Option(names = "--rules", description = "Managed profile name, packaged quality profile name, or JSON/YAML rule file")
  String rulesProfileOrFile;

  @Option(
    names = "--enable",
    split = ",",
    description = "Enable one or more rules",
    completionCandidates = CliCompletionCandidates.RuleSelectors.class
  )
  List<String> enable = new ArrayList<>();

  @Option(
    names = "--disable",
    split = ",",
    description = "Disable one or more rules",
    completionCandidates = CliCompletionCandidates.RuleSelectors.class
  )
  List<String> disable = new ArrayList<>();

  @Option(
    names = "--analyzer",
    split = ",",
    description = "Analyzers to run: ${COMPLETION-CANDIDATES}",
    completionCandidates = CliCompletionCandidates.AnalyzerIds.class
  )
  List<String> analyzers = new ArrayList<>();

  @Option(names = "--base-dir", description = "Base directory for project-relative paths")
  Path baseDir;

  @Option(names = "--tsconfig", description = "Explicit tsconfig path", split = ",")
  List<Path> tsconfigs = new ArrayList<>();

  @Option(names = "--exclude", description = "Glob exclusion", split = ",")
  List<String> excludes = new ArrayList<>();

  @Option(names = "--include", description = "Glob inclusion", split = ",")
  List<String> includes = new ArrayList<>();

  @Option(names = "--tests", description = "Glob identifying test files", split = ",")
  List<String> tests = new ArrayList<>();

  @Option(names = "--max-file-size-kb", defaultValue = "1000", description = "Maximum file size in KB")
  long maxFileSizeKb = 1000L;

  @Option(names = "--max-files-for-type-checking", defaultValue = "20000", description = "Maximum number of files for JS/TS type-aware analysis")
  int maxFilesForTypeChecking = 20_000;

  @Option(names = "--globals", split = ",", description = "Comma-separated JS globals")
  List<String> globals = new ArrayList<>();

  @Option(names = "--env", split = ",", description = "Comma-separated JS environments")
  List<String> environments = new ArrayList<>();

  @Option(names = "--debug", description = "Enable verbose logging")
  boolean debug;

  @Option(names = "--debug-bridge", description = "Enable bridge-specific JS debug logging")
  boolean debugBridge;

  @Option(names = "--debug-memory", description = "Enable JS Node bridge memory diagnostics")
  boolean debugMemory;

  @Option(names = "--node", description = "Path to a host Node.js executable")
  String nodeExecutable;

  @Option(names = "--skip-node-provisioning", description = "Skip embedded Node.js provisioning")
  boolean skipNodeProvisioning;

  @Option(names = "--java-binaries", split = ",", description = "Paths for sonar.java.binaries")
  List<Path> javaBinaries = new ArrayList<>();

  @Option(names = "--java-libraries", split = ",", description = "Paths for sonar.java.libraries")
  List<Path> javaLibraries = new ArrayList<>();

  @Option(names = "--java-test-binaries", split = ",", description = "Paths for sonar.java.test.binaries")
  List<Path> javaTestBinaries = new ArrayList<>();

  @Option(names = "--java-test-libraries", split = ",", description = "Paths for sonar.java.test.libraries")
  List<Path> javaTestLibraries = new ArrayList<>();

  @Option(names = "--java-source", description = "Value for sonar.java.source")
  String javaSourceVersion;

  @Option(names = "--java-enable-preview", description = "Enable preview features for Java analysis")
  boolean javaEnablePreview;

  @Option(names = "--java-jdk-home", description = "Value for sonar.java.jdkHome")
  Path javaJdkHome;

  AnalysisParameters toParameters(Path inferredBaseDir, List<String> resolvedAnalyzers) {
    return new AnalysisParameters(
      inferredBaseDir.toAbsolutePath().normalize(),
      List.copyOf(resolvedAnalyzers),
      tsconfigs.stream().map(path -> path.toAbsolutePath().normalize()).toList(),
      List.copyOf(includes),
      List.copyOf(excludes),
      List.copyOf(tests),
      maxFileSizeKb * 1024L,
      maxFilesForTypeChecking,
      List.copyOf(globals),
      List.copyOf(environments),
      nodeExecutable,
      skipNodeProvisioning,
      debugMemory,
      normalizePaths(javaBinaries),
      normalizePaths(javaLibraries),
      normalizePaths(javaTestBinaries),
      normalizePaths(javaTestLibraries),
      javaSourceVersion,
      javaEnablePreview,
      javaJdkHome == null ? null : javaJdkHome.toAbsolutePath().normalize()
    );
  }

  private static List<Path> normalizePaths(List<Path> paths) {
    return paths.stream().map(path -> path.toAbsolutePath().normalize()).toList();
  }
}
