package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.analyzers.java.JavaAnalyzerModule;
import com.donatomartin.sonarcli.analyzers.js.JsAnalyzerModule;
import com.donatomartin.sonarcli.core.catalog.RuleCatalog;
import com.donatomartin.sonarcli.core.catalog.SelectedRuleSet;
import com.donatomartin.sonarcli.core.model.AnalysisReport;
import com.donatomartin.sonarcli.core.model.ReportStats;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.runtime.AnalysisParameters;
import com.donatomartin.sonarcli.core.runtime.AnalyzerModule;
import com.donatomartin.sonarcli.core.runtime.AnalyzerResult;
import com.donatomartin.sonarcli.core.scan.DiscoveredProject;
import com.donatomartin.sonarcli.core.scan.ProjectScanner;
import com.donatomartin.sonarcli.core.scan.ScannedFile;
import com.donatomartin.sonarcli.core.util.IssueComparators;
import com.donatomartin.sonarcli.core.util.RuleSelectorSupport;
import com.donatomartin.sonarcli.core.util.TextSupport;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
  name = "sonar-analyzer-cli",
  mixinStandardHelpOptions = true,
  description = "Standalone Sonar JS + Java analyzer CLI",
  subcommands = {
    SonarAnalyzerCli.RulesCommand.class,
    SonarAnalyzerCli.AnalyzeCommand.class,
    SonarAnalyzerCli.AnalyzeFileCommand.class,
    SonarAnalyzerCli.DoctorCommand.class
  }
)
public final class SonarAnalyzerCli implements Runnable {

  private static final String CLI_VERSION = "0.2.0-SNAPSHOT";
  private static final ReportRenderer REPORT_RENDERER = new ReportRenderer();
  private static final ProjectScanner PROJECT_SCANNER = new ProjectScanner();
  private static final List<AnalyzerModule> MODULES = List.of(new JsAnalyzerModule(), new JavaAnalyzerModule());
  private static final Map<String, AnalyzerModule> MODULES_BY_ID = MODULES.stream().collect(
    LinkedHashMap::new,
    (map, module) -> map.put(module.id(), module),
    Map::putAll
  );

  public static void main(String[] args) {
    var commandLine = new CommandLine(new SonarAnalyzerCli());
    commandLine.setExecutionExceptionHandler((exception, cmd, parseResult) -> handleException(exception, cmd));
    int exitCode = commandLine.execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  private static int handleException(Exception exception, CommandLine commandLine) {
    Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
      ? exception.getCause()
      : exception;
    PrintWriter err = commandLine.getErr();
    err.println(cause.getMessage());
    if (!(cause instanceof IllegalArgumentException)) {
      cause.printStackTrace(err);
      return 3;
    }
    return 2;
  }

  @Command(name = "rules", mixinStandardHelpOptions = true, description = "Inspect the packaged rule catalog", subcommands = {
    RulesListCommand.class,
    RulesShowCommand.class,
    RulesProfilesCommand.class
  })
  static final class RulesCommand implements Runnable {
    @Override
    public void run() {
      CommandLine.usage(this, System.out);
    }
  }

  @Command(name = "list", mixinStandardHelpOptions = true, description = "List available rules")
  static final class RulesListCommand implements Callable<Integer> {

    @Option(names = "--family", description = "Filter by family")
    String family;

    @Option(names = "--analyzer", split = ",", description = "Filter by analyzer")
    List<String> analyzers = new ArrayList<>();

    @Override
    public Integer call() {
      var analyzerIds = normalizeAnalyzerIds(analyzers);
      var catalog = loadCatalog();
      var rules = catalog.allRules(analyzerIds).stream().filter(rule -> rule.matchesFamily(family)).toList();
      var lines = new ArrayList<String>();
      for (RuleDefinition rule : rules) {
        lines.add(
          String.format(
            Locale.ROOT,
            "%-18s  %-6s  %-6s  %-18s  %-10s  %s",
            rule.selector(),
            rule.analyzerId(),
            String.join(",", rule.languages()),
            nullToDash(rule.type()),
            nullToDash(rule.defaultSeverity()),
            rule.title()
          )
        );
      }
      REPORT_RENDERER.printRuleList(lines);
      return 0;
    }
  }

  @Command(name = "show", mixinStandardHelpOptions = true, description = "Show metadata for one rule")
  static final class RulesShowCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Rule selector or raw key")
    String ruleKey;

    @Override
    public Integer call() {
      var matches = loadCatalog().find(ruleKey);
      if (matches.isEmpty()) {
        throw new IllegalArgumentException("Unknown rule selector: " + ruleKey);
      }
      var lines = new ArrayList<String>();
      for (RuleDefinition rule : matches) {
        var displayedSelector = RuleSelectorSupport.repoSelectors(rule).stream()
          .filter(selector -> selector.equalsIgnoreCase(ruleKey.trim()))
          .findFirst()
          .orElse(rule.selector());
        lines.add(displayedSelector);
        if (!displayedSelector.equals(rule.selector())) {
          lines.add("  Canonical: " + rule.selector());
        }
        var aliases = RuleSelectorSupport.repoSelectors(rule).stream()
          .filter(selector -> !selector.equals(displayedSelector))
          .toList();
        if (!aliases.isEmpty()) {
          lines.add("  Aliases: " + String.join(", ", aliases));
        }
        lines.add("  Raw key: " + rule.rawKey());
        lines.add("  Title: " + rule.title());
        lines.add("  Analyzer: " + rule.analyzerId());
        lines.add("  Family: " + rule.family());
        lines.add("  Languages: " + String.join(", ", rule.languages()));
        lines.add("  Type: " + nullToDash(rule.type()));
        lines.add("  Severity: " + nullToDash(rule.defaultSeverity()));
        lines.add("  Profiles: " + String.join(", ", rule.defaultQualityProfiles()));
        if (!rule.tags().isEmpty()) {
          lines.add("  Tags: " + String.join(", ", rule.tags()));
        }
        if (rule.ruleSpecification() != null && !rule.ruleSpecification().isBlank()) {
          lines.add("  Spec: " + rule.ruleSpecification());
        }
        var textDescription = TextSupport.stripHtml(rule.descriptionHtml());
        if (!textDescription.isBlank()) {
          lines.add("  Description:");
          for (String line : textDescription.split("\\R")) {
            if (!line.isBlank()) {
              lines.add("    " + line);
            }
          }
        }
        lines.add("");
      }
      REPORT_RENDERER.printRuleList(lines);
      return 0;
    }
  }

  @Command(name = "profiles", mixinStandardHelpOptions = true, description = "List packaged quality profiles")
  static final class RulesProfilesCommand implements Callable<Integer> {

    @Option(names = "--analyzer", split = ",", description = "Filter by analyzer")
    List<String> analyzers = new ArrayList<>();

    @Override
    public Integer call() {
      var analyzerIds = normalizeAnalyzerIds(analyzers);
      var rules = loadCatalog().allRules(analyzerIds);
      var profileCounts = new LinkedHashMap<String, Integer>();
      var profileAnalyzers = new LinkedHashMap<String, Set<String>>();
      for (RuleDefinition rule : rules) {
        for (String profile : rule.defaultQualityProfiles()) {
          profileCounts.merge(profile, 1, Integer::sum);
          profileAnalyzers.computeIfAbsent(profile, ignored -> new LinkedHashSet<>()).add(rule.analyzerId());
        }
      }
      var lines = new ArrayList<String>();
      for (Map.Entry<String, Integer> entry : profileCounts.entrySet()) {
        lines.add(
          String.format(
            Locale.ROOT,
            "%-24s  %-8s  %d rules",
            entry.getKey(),
            String.join(",", profileAnalyzers.getOrDefault(entry.getKey(), Set.of())),
            entry.getValue()
          )
        );
      }
      if (lines.isEmpty()) {
        lines.add("No profiles found.");
      }
      REPORT_RENDERER.printRuleList(lines);
      return 0;
    }
  }

  @Command(name = "analyze", mixinStandardHelpOptions = true, description = "Analyze a directory recursively")
  static final class AnalyzeCommand implements Callable<Integer> {

    @Parameters(index = "0", defaultValue = ".", description = "Directory to analyze")
    Path path;

    @Mixin
    AnalysisOptionsMixin options;

    @Override
    public Integer call() throws Exception {
      var absolutePath = path.toAbsolutePath().normalize();
      var baseDir = options.baseDir != null ? options.baseDir : absolutePath;
      var project = PROJECT_SCANNER.scan(
        absolutePath,
        options.includes,
        options.excludes,
        options.tests,
        options.tsconfigs
      );
      var analyzerIds = resolveProjectAnalyzers(project, options.analyzers);
      return runProjectAnalysis(baseDir.toAbsolutePath().normalize(), project, analyzerIds, options);
    }
  }

  @Command(name = "analyze-file", mixinStandardHelpOptions = true, description = "Analyze one file")
  static final class AnalyzeFileCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "File to analyze")
    Path file;

    @Mixin
    AnalysisOptionsMixin options;

    @Override
    public Integer call() throws Exception {
      var absoluteFile = file.toAbsolutePath().normalize();
      if (!Files.isRegularFile(absoluteFile)) {
        throw new IllegalArgumentException("Not a file: " + absoluteFile);
      }
      var baseDir = options.baseDir != null ? options.baseDir : ProjectRootResolver.inferBaseDir(absoluteFile);
      var relative = baseDir.toAbsolutePath().normalize().relativize(absoluteFile).toString().replace('\\', '/');
      var project = PROJECT_SCANNER.scan(
        baseDir,
        List.of(relative),
        options.excludes,
        options.tests,
        options.tsconfigs
      );
      var target = project.files().stream()
        .filter(candidate -> candidate.path().equals(absoluteFile))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("File is not analyzable: " + absoluteFile));
      var analyzerIds = resolveFileAnalyzers(target, options.analyzers);
      return runSingleFileAnalysis(baseDir.toAbsolutePath().normalize(), project, target, analyzerIds, options);
    }
  }

  @Command(name = "doctor", mixinStandardHelpOptions = true, description = "Validate local runtime prerequisites")
  static final class DoctorCommand implements Callable<Integer> {

    @Parameters(index = "0", defaultValue = ".", description = "Directory to inspect")
    Path path;

    @Mixin
    AnalysisOptionsMixin options;

    @Override
    public Integer call() throws Exception {
      configureLogging(options.debug, options.debugBridge);
      var absolutePath = path.toAbsolutePath().normalize();
      var baseDir = options.baseDir != null ? options.baseDir : absolutePath;
      var project = PROJECT_SCANNER.scan(
        absolutePath,
        options.includes,
        options.excludes,
        options.tests,
        options.tsconfigs
      );
      var analyzerIds = options.analyzers.isEmpty() ? allAnalyzerIds() : normalizeAnalyzerIds(options.analyzers);
      var parameters = options.toParameters(baseDir.toAbsolutePath().normalize(), List.copyOf(analyzerIds));
      var lines = new ArrayList<String>();
      for (String analyzerId : analyzerIds) {
        var module = requireModule(analyzerId);
        lines.add("[" + analyzerId + "]");
        lines.addAll(module.doctor(project, parameters));
        lines.add("");
      }
      REPORT_RENDERER.printDoctor(lines);
      return 0;
    }
  }

  private static Integer runProjectAnalysis(
    Path baseDir,
    DiscoveredProject project,
    Set<String> analyzerIds,
    AnalysisOptionsMixin options
  ) throws Exception {
    configureLogging(options.debug, options.debugBridge);
    var parameters = options.toParameters(baseDir, List.copyOf(analyzerIds));
    var catalog = loadCatalog();
    var selectedRules = catalog.resolveSelection(
      options.rulesProfileOrFile,
      options.enable,
      options.disable,
      analyzerIds
    );
    var report = analyzeProject(project, analyzerIds, selectedRules, parameters);
    REPORT_RENDERER.writeReport(report, options.format, options.output);
    return report.issues().isEmpty() ? 0 : 1;
  }

  private static Integer runSingleFileAnalysis(
    Path baseDir,
    DiscoveredProject project,
    ScannedFile target,
    Set<String> analyzerIds,
    AnalysisOptionsMixin options
  ) throws Exception {
    configureLogging(options.debug, options.debugBridge);
    var parameters = options.toParameters(baseDir, List.copyOf(analyzerIds));
    var catalog = loadCatalog();
    var selectedRules = catalog.resolveSelection(
      options.rulesProfileOrFile,
      options.enable,
      options.disable,
      analyzerIds
    );
    var report = analyzeFile(project, target, analyzerIds, selectedRules, parameters);
    REPORT_RENDERER.writeReport(report, options.format, options.output);
    return report.issues().isEmpty() ? 0 : 1;
  }

  private static AnalysisReport analyzeProject(
    DiscoveredProject project,
    Set<String> analyzerIds,
    SelectedRuleSet selectedRules,
    AnalysisParameters parameters
  ) throws Exception {
    Instant start = Instant.now();
    var results = new ArrayList<AnalyzerResult>();
    for (String analyzerId : analyzerIds) {
      var module = requireModule(analyzerId);
      var hasFiles = !project.filesForAnalyzer(analyzerId).isEmpty();
      var hasRules = !selectedRules.forAnalyzer(analyzerId).isEmpty();
      if (!hasFiles || !hasRules) {
        continue;
      }
      results.add(module.analyzeProject(project, selectedRules, parameters));
    }
    return mergeReport(project.baseDir(), analyzerIds, selectedRules, results, Duration.between(start, Instant.now()).toMillis());
  }

  private static AnalysisReport analyzeFile(
    DiscoveredProject project,
    ScannedFile target,
    Set<String> analyzerIds,
    SelectedRuleSet selectedRules,
    AnalysisParameters parameters
  ) throws Exception {
    Instant start = Instant.now();
    var results = new ArrayList<AnalyzerResult>();
    for (String analyzerId : analyzerIds) {
      var module = requireModule(analyzerId);
      if (!module.supportedFileKinds().contains(target.kind())) {
        continue;
      }
      if (selectedRules.forAnalyzer(analyzerId).isEmpty()) {
        continue;
      }
      results.add(module.analyzeFile(project, target, selectedRules, parameters));
    }
    return mergeReport(project.baseDir(), analyzerIds, selectedRules, results, Duration.between(start, Instant.now()).toMillis());
  }

  private static AnalysisReport mergeReport(
    Path baseDir,
    Collection<String> analyzerIds,
    SelectedRuleSet selectedRules,
    List<AnalyzerResult> results,
    long durationMs
  ) {
    var analyzerVersions = new LinkedHashMap<String, String>();
    var warnings = new ArrayList<String>();
    warnings.addAll(selectedRules.selectionWarnings());
    var issues = new ArrayList<com.donatomartin.sonarcli.core.model.IssueRecord>();
    var fileCounts = new LinkedHashMap<String, Integer>();
    int parsingErrors = 0;

    for (AnalyzerResult result : results) {
      analyzerVersions.put(result.analyzerId(), result.analyzerVersion());
      warnings.addAll(result.warnings().stream().map(warning -> "[" + result.analyzerId() + "] " + warning).toList());
      issues.addAll(result.issues());
      parsingErrors += result.parsingErrors();
      for (Map.Entry<String, Integer> entry : result.fileCounts().entrySet()) {
        fileCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }

    return new AnalysisReport(
      "sonar-analyzer-cli/v2",
      CLI_VERSION,
      baseDir.toString(),
      List.copyOf(analyzerIds),
      Map.copyOf(analyzerVersions),
      selectedRules.selectedRules().stream().map(RuleDefinition::selector).toList(),
      List.copyOf(warnings),
      issues.stream().sorted(IssueComparators.ISSUE_ORDER).toList(),
      new ReportStats(
        fileCounts.values().stream().mapToInt(Integer::intValue).sum(),
        Map.copyOf(fileCounts),
        issues.size(),
        parsingErrors,
        durationMs
      )
    );
  }

  private static RuleCatalog loadCatalog() {
    var rules = new ArrayList<RuleDefinition>();
    for (AnalyzerModule module : MODULES) {
      rules.addAll(module.loadRules());
    }
    return new RuleCatalog(rules);
  }

  private static AnalyzerModule requireModule(String analyzerId) {
    var module = MODULES_BY_ID.get(analyzerId);
    if (module == null) {
      throw new IllegalArgumentException("Unknown analyzer: " + analyzerId);
    }
    return module;
  }

  private static Set<String> resolveProjectAnalyzers(DiscoveredProject project, List<String> requestedAnalyzers) {
    if (!requestedAnalyzers.isEmpty()) {
      return normalizeAnalyzerIds(requestedAnalyzers);
    }
    var discovered = new LinkedHashSet<String>();
    for (ScannedFile file : project.files()) {
      discovered.add(file.kind().analyzerId());
    }
    return discovered.isEmpty() ? allAnalyzerIds() : discovered;
  }

  private static Set<String> resolveFileAnalyzers(ScannedFile file, List<String> requestedAnalyzers) {
    if (requestedAnalyzers.isEmpty()) {
      return Set.of(file.kind().analyzerId());
    }
    var analyzerIds = normalizeAnalyzerIds(requestedAnalyzers);
    if (!analyzerIds.contains(file.kind().analyzerId())) {
      throw new IllegalArgumentException("Requested analyzers do not support file kind: " + file.kind());
    }
    return analyzerIds;
  }

  private static Set<String> normalizeAnalyzerIds(List<String> analyzers) {
    var normalized = new LinkedHashSet<String>();
    for (String analyzer : analyzers) {
      if (analyzer == null || analyzer.isBlank()) {
        continue;
      }
      var normalizedValue = analyzer.trim().toLowerCase(Locale.ROOT);
      requireModule(normalizedValue);
      normalized.add(normalizedValue);
    }
    return normalized;
  }

  private static Set<String> allAnalyzerIds() {
    return new LinkedHashSet<>(MODULES_BY_ID.keySet());
  }

  private static void configureLogging(boolean debug, boolean debugBridge) {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", debug ? "debug" : "error");
    System.setProperty("org.slf4j.simpleLogger.log.org.sonar.plugins.javascript.bridge", debugBridge ? "debug" : "off");
    System.setProperty("org.slf4j.simpleLogger.log.org.sonar.plugins.javascript.nodejs", debugBridge ? "debug" : "off");
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
    System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }
}
