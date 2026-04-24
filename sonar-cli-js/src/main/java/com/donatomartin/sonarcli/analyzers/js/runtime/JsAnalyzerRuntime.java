package com.donatomartin.sonarcli.analyzers.js.runtime;

import com.donatomartin.sonarcli.analyzers.js.model.CssRuntimeRule;
import com.donatomartin.sonarcli.analyzers.js.model.JsRuleEntry;
import com.donatomartin.sonarcli.analyzers.js.model.JsRuntimeRule;
import com.donatomartin.sonarcli.core.model.IssueLocationRecord;
import com.donatomartin.sonarcli.core.model.IssueRecord;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.runtime.AnalysisParameters;
import com.donatomartin.sonarcli.core.runtime.AnalyzerResult;
import com.donatomartin.sonarcli.core.scan.DiscoveredProject;
import com.donatomartin.sonarcli.core.scan.FileKind;
import com.donatomartin.sonarcli.core.scan.ScannedFile;
import com.donatomartin.sonarcli.core.util.RuleSelectorSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sonar.api.SonarProduct;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.plugins.javascript.api.AnalysisMode;
import org.sonar.plugins.javascript.bridge.AnalysisWarningsWrapper;
import org.sonar.plugins.javascript.bridge.BridgeServer;
import org.sonar.plugins.javascript.bridge.BridgeServerConfig;
import org.sonar.plugins.javascript.bridge.BridgeServerImpl;
import org.sonar.plugins.javascript.bridge.BundleImpl;
import org.sonar.plugins.javascript.bridge.EmbeddedNode;
import org.sonar.plugins.javascript.bridge.Environment;
import org.sonar.plugins.javascript.bridge.NodeDeprecationWarning;
import org.sonar.plugins.javascript.bridge.RulesBundles;
import org.sonar.plugins.javascript.nodejs.NodeCommandBuilderImpl;
import org.sonar.plugins.javascript.nodejs.ProcessWrapperImpl;

public final class JsAnalyzerRuntime implements AutoCloseable {

  public static final String ANALYZER_VERSION = "11.7.1.36988";
  private static final String NODE_EXECUTABLE_PROPERTY = "sonar.nodejs.executable";
  private static final String SKIP_NODE_PROVISIONING_PROPERTY = "sonar.scanner.skipNodeProvisioning";
  private static final String DEBUG_MEMORY_PROPERTY = "sonar.javascript.node.debugMemory";

  private final AnalysisParameters parameters;
  private final MapBackedConfiguration configuration;
  private final BridgeServerImpl bridgeServer;
  private final AtomicBoolean started = new AtomicBoolean(false);

  public JsAnalyzerRuntime(AnalysisParameters parameters) {
    this.parameters = parameters;
    this.configuration = new MapBackedConfiguration(buildConfiguration(parameters));
    var tempFolder = new CliTempFolder();
    var processWrapper = new ProcessWrapperImpl();
    var embeddedNode = new EmbeddedNode(processWrapper, new Environment(configuration));
    this.bridgeServer = new BridgeServerImpl(
      new NodeCommandBuilderImpl(processWrapper),
      new BundleImpl(),
      new RulesBundles(),
      new NodeDeprecationWarning(new AnalysisWarningsWrapper()),
      tempFolder,
      embeddedNode
    );
  }

  public AnalyzerResult analyzeProject(DiscoveredProject project, List<JsRuleEntry> selectedRules) throws IOException {
    ensureStarted(project, selectedRules);
    var runtimeRules = resolveRules(selectedRules);
    var bridgeConfiguration = bridgeConfiguration(project);
    var warnings = new ArrayList<String>();
    var issues = new ArrayList<IssueRecord>();
    int parsingErrors = 0;

    if (!project.filesOfKind(FileKind.JS_TS).isEmpty() && !runtimeRules.jsRuntimeRules().isEmpty()) {
      boolean clearDependenciesCache = true;
      for (ScannedFile file : project.filesOfKind(FileKind.JS_TS)) {
        parsingErrors += appendIssues(
          project.baseDir(),
          file,
          analyzeJsTsFile(file, bridgeConfiguration, project, clearDependenciesCache),
          runtimeRules.ruleByRawKey(),
          runtimeRules.cssRuleKeyByStylelintKey(),
          warnings,
          issues
        );
        clearDependenciesCache = false;
      }
    }
    if (!runtimeRules.jsRuntimeRules().isEmpty()) {
      for (ScannedFile file : project.filesOfKind(FileKind.HTML)) {
        parsingErrors += appendIssues(
          project.baseDir(),
          file,
          analyzeMarkupFile(file, bridgeConfiguration, true),
          runtimeRules.ruleByRawKey(),
          runtimeRules.cssRuleKeyByStylelintKey(),
          warnings,
          issues
        );
      }
      for (ScannedFile file : project.filesOfKind(FileKind.YAML)) {
        parsingErrors += appendIssues(
          project.baseDir(),
          file,
          analyzeMarkupFile(file, bridgeConfiguration, false),
          runtimeRules.ruleByRawKey(),
          runtimeRules.cssRuleKeyByStylelintKey(),
          warnings,
          issues
        );
      }
    }
    if (!project.filesOfKind(FileKind.CSS).isEmpty() && !runtimeRules.cssRuntimeRules().isEmpty()) {
      for (ScannedFile file : project.filesOfKind(FileKind.CSS)) {
        parsingErrors += appendIssues(
          project.baseDir(),
          file,
          analyzeCssFile(file, bridgeConfiguration, runtimeRules),
          runtimeRules.ruleByRawKey(),
          runtimeRules.cssRuleKeyByStylelintKey(),
          warnings,
          issues
        );
      }
    }

    return new AnalyzerResult(
      "js",
      ANALYZER_VERSION,
      List.copyOf(warnings),
      issues,
      parsingErrors,
      jsFileCounts(project)
    );
  }

  public AnalyzerResult analyzeFile(DiscoveredProject project, ScannedFile file, List<JsRuleEntry> selectedRules) throws IOException {
    ensureStarted(project, selectedRules);
    var runtimeRules = resolveRules(selectedRules);
    var bridgeConfiguration = bridgeConfiguration(project);
    var warnings = new ArrayList<String>();
    var issues = new ArrayList<IssueRecord>();
    BridgeServer.AnalysisResponse response = switch (file.kind()) {
      case JS_TS -> analyzeJsTsFile(file, bridgeConfiguration, project, true);
      case CSS -> analyzeCssFile(file, bridgeConfiguration, runtimeRules);
      case HTML -> analyzeMarkupFile(file, bridgeConfiguration, true);
      case YAML -> analyzeMarkupFile(file, bridgeConfiguration, false);
      default -> throw new IllegalArgumentException("Unsupported file kind for JS analyzer: " + file.kind());
    };

    int parsingErrors = appendIssues(
      project.baseDir(),
      file,
      response,
      runtimeRules.ruleByRawKey(),
      runtimeRules.cssRuleKeyByStylelintKey(),
      warnings,
      issues
    );

    var counts = new LinkedHashMap<String, Integer>();
    counts.put("js-ts", file.kind() == FileKind.JS_TS ? 1 : 0);
    counts.put("html", file.kind() == FileKind.HTML ? 1 : 0);
    counts.put("yaml", file.kind() == FileKind.YAML ? 1 : 0);
    counts.put("css", file.kind() == FileKind.CSS ? 1 : 0);
    counts.put("java", 0);
    return new AnalyzerResult("js", ANALYZER_VERSION, List.copyOf(warnings), issues, parsingErrors, Map.copyOf(counts));
  }

  public List<String> doctor(DiscoveredProject project) {
    var lines = new ArrayList<String>();
    lines.add("Analyzer version: " + ANALYZER_VERSION);
    lines.add("Java runtime: " + Runtime.version());
    lines.add("Bridge bundle resource present: " + (JsAnalyzerRuntime.class.getResource("/sonarjs-1.0.0.tgz") != null));
    lines.add("Host Node.js: " + detectNodeVersion());
    lines.add("Detected tsconfig files: " + project.tsconfigPaths().size());
    for (Path tsconfig : project.tsconfigPaths()) {
      lines.add("  - " + relativePath(project.baseDir(), tsconfig));
    }
    return List.copyOf(lines);
  }

  @Override
  public void close() {
    bridgeServer.stop();
  }

  private void ensureStarted(DiscoveredProject project, List<JsRuleEntry> selectedRules) throws IOException {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    var runtimeRules = resolveRules(selectedRules);
    var workDir = Files.createTempDirectory("sonar-analyzer-cli-js-work");
    bridgeServer.startServerLazily(new BridgeServerConfig(configuration, workDir.toString(), SonarProduct.SONARLINT));
    bridgeServer.initLinter(
      runtimeRules.jsRuntimeRules(),
      parameters.environments(),
      parameters.globals(),
      project.baseDir().toString(),
      true
    );
  }

  private BridgeServer.ProjectAnalysisConfiguration bridgeConfiguration(DiscoveredProject project) {
    var analysisConfiguration = new JsAnalysisConfigurationAdapter(parameters, project);
    var bridgeConfiguration = new BridgeServer.ProjectAnalysisConfiguration(project.baseDir().toString(), analysisConfiguration);
    bridgeConfiguration.setSkipAst(true);
    return bridgeConfiguration;
  }

  private static ResolvedRules resolveRules(List<JsRuleEntry> selectedRules) {
    var jsRuntimeRules = new ArrayList<org.sonar.plugins.javascript.bridge.EslintRule>();
    var cssRuntimeRules = new ArrayList<org.sonar.plugins.javascript.bridge.StylelintRule>();
    var cssRuleKeyByStylelintKey = new LinkedHashMap<String, String>();
    var ruleByRawKey = new LinkedHashMap<String, RuleDefinition>();

    for (JsRuleEntry entry : selectedRules) {
      ruleByRawKey.put(entry.definition().rawKey(), entry.definition());
      for (JsRuntimeRule runtimeRule : entry.jsRuntimeRules()) {
        jsRuntimeRules.add(
          new org.sonar.plugins.javascript.bridge.EslintRule(
            runtimeRule.rawKey(),
            runtimeRule.configurations(),
            runtimeRule.targets(),
            runtimeRule.analysisModes(),
            runtimeRule.blacklistedExtensions(),
            runtimeRule.language()
          )
        );
      }
      if (entry.cssRuntimeRule() != null) {
        cssRuntimeRules.add(
          new org.sonar.plugins.javascript.bridge.StylelintRule(
            entry.cssRuntimeRule().stylelintKey(),
            entry.cssRuntimeRule().configurations()
          )
        );
        cssRuleKeyByStylelintKey.put(entry.cssRuntimeRule().stylelintKey(), entry.cssRuntimeRule().rawKey());
      }
    }
    return new ResolvedRules(
      List.copyOf(jsRuntimeRules),
      List.copyOf(cssRuntimeRules),
      Map.copyOf(cssRuleKeyByStylelintKey),
      Map.copyOf(ruleByRawKey)
    );
  }

  private BridgeServer.AnalysisResponse analyzeMarkupFile(
    ScannedFile file,
    BridgeServer.ProjectAnalysisConfiguration bridgeConfiguration,
    boolean html
  ) throws IOException {
    var request = new BridgeServer.JsAnalysisRequest(
      file.path().toString(),
      toInputFileType(file).name(),
      Files.readString(file.path()),
      true,
      null,
      null,
      InputFile.Status.ADDED,
      AnalysisMode.DEFAULT,
      true,
      false,
      true,
      true,
      bridgeConfiguration
    );
    return html ? bridgeServer.analyzeHtml(request) : bridgeServer.analyzeYaml(request);
  }

  private BridgeServer.AnalysisResponse analyzeJsTsFile(
    ScannedFile file,
    BridgeServer.ProjectAnalysisConfiguration bridgeConfiguration,
    DiscoveredProject project,
    boolean clearDependenciesCache
  ) throws IOException {
    var tsconfigs = project.tsconfigPaths().isEmpty() ? null : project.tsconfigPaths().stream().map(Path::toString).toList();
    var request = new BridgeServer.JsAnalysisRequest(
      file.path().toString(),
      toInputFileType(file).name(),
      Files.readString(file.path()),
      true,
      tsconfigs,
      null,
      InputFile.Status.ADDED,
      AnalysisMode.DEFAULT,
      true,
      clearDependenciesCache,
      true,
      true,
      bridgeConfiguration
    );
    return bridgeServer.analyzeJsTs(request);
  }

  private BridgeServer.AnalysisResponse analyzeCssFile(
    ScannedFile file,
    BridgeServer.ProjectAnalysisConfiguration bridgeConfiguration,
    ResolvedRules runtimeRules
  ) throws IOException {
    return bridgeServer.analyzeCss(
      new BridgeServer.CssAnalysisRequest(
        file.path().toString(),
        Files.readString(file.path()),
        runtimeRules.cssRuntimeRules(),
        bridgeConfiguration
      )
    );
  }

  private int appendIssues(
    Path baseDir,
    ScannedFile file,
    BridgeServer.AnalysisResponse response,
    Map<String, RuleDefinition> ruleByRawKey,
    Map<String, String> cssRuleKeyByStylelintKey,
    List<String> warnings,
    List<IssueRecord> issues
  ) {
    int parsingErrors = 0;
    if (response.parsingError() != null) {
      parsingErrors += 1;
      warnings.add(relativePath(baseDir, file.path()) + ":" + response.parsingError().line() + " parse error: " + response.parsingError().message());
    }

    for (BridgeServer.Issue issue : response.issues()) {
      if ("CssSyntaxError".equals(issue.ruleId())) {
        parsingErrors += 1;
        warnings.add(relativePath(baseDir, file.path()) + ":" + issue.line() + " parse error: " + issue.message());
        continue;
      }
      var rawRuleKey = cssRuleKeyByStylelintKey.getOrDefault(issue.ruleId(), issue.ruleId());
      var rule = ruleByRawKey.get(rawRuleKey);
      issues.add(
        new IssueRecord(
          "js",
          rule != null ? rule.family() : inferFamily(file),
          rawRuleKey,
          RuleSelectorSupport.selectorForIssue(
            rule,
            rawRuleKey,
            issue.language(),
            issue.filePath() != null ? Path.of(issue.filePath()) : file.path()
          ),
          relativePath(baseDir, issue.filePath() != null ? Path.of(issue.filePath()) : file.path()),
          issue.line() == null ? 1 : issue.line(),
          issue.column() == null ? 0 : issue.column(),
          issue.endLine() == null ? (issue.line() == null ? 1 : issue.line()) : issue.endLine(),
          issue.endColumn() == null ? (issue.column() == null ? 0 : issue.column()) : issue.endColumn(),
          issue.message(),
          rule != null ? rule.defaultSeverity() : null,
          rule != null ? rule.type() : null,
          issue.secondaryLocations() == null
            ? List.of()
            : issue.secondaryLocations().stream().map(location -> new IssueLocationRecord(
              relativePath(baseDir, file.path()),
              location.line(),
              location.column(),
              location.endLine(),
              location.endColumn(),
              location.message()
            )).toList()
        )
      );
    }
    return parsingErrors;
  }

  private static org.sonar.api.batch.fs.InputFile.Type toInputFileType(ScannedFile file) {
    return file.sourceType() == com.donatomartin.sonarcli.core.model.SourceType.TEST
      ? org.sonar.api.batch.fs.InputFile.Type.TEST
      : org.sonar.api.batch.fs.InputFile.Type.MAIN;
  }

  private static com.donatomartin.sonarcli.core.model.RuleFamily inferFamily(ScannedFile file) {
    return file.kind() == FileKind.CSS
      ? com.donatomartin.sonarcli.core.model.RuleFamily.CSS
      : com.donatomartin.sonarcli.core.model.RuleFamily.JS;
  }

  private static String relativePath(Path baseDir, Path path) {
    try {
      return baseDir.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    } catch (IllegalArgumentException ignored) {
      return path.toString();
    }
  }

  private String detectNodeVersion() {
    var executable = parameters.nodeExecutable() != null && !parameters.nodeExecutable().isBlank()
      ? parameters.nodeExecutable()
      : "node";
    try {
      var process = new ProcessBuilder(executable, "--version").start();
      var stdout = new String(process.getInputStream().readAllBytes()).trim();
      var stderr = new String(process.getErrorStream().readAllBytes()).trim();
      var exitCode = process.waitFor();
      if (exitCode == 0) {
        return stdout;
      }
      return "unavailable (" + stderr + ")";
    } catch (Exception e) {
      return "unavailable (" + e.getMessage() + ")";
    }
  }

  private static Map<String, String> buildConfiguration(AnalysisParameters parameters) {
    var values = new LinkedHashMap<String, String>();
    if (parameters.nodeExecutable() != null && !parameters.nodeExecutable().isBlank()) {
      values.put(NODE_EXECUTABLE_PROPERTY, parameters.nodeExecutable());
    }
    if (parameters.skipNodeProvisioning()) {
      values.put(SKIP_NODE_PROVISIONING_PROPERTY, "true");
    }
    if (parameters.debugMemory()) {
      values.put(DEBUG_MEMORY_PROPERTY, "true");
    }
    return values;
  }

  private static Map<String, Integer> jsFileCounts(DiscoveredProject project) {
    var counts = new LinkedHashMap<String, Integer>();
    counts.put("js-ts", project.filesOfKind(FileKind.JS_TS).size());
    counts.put("html", project.filesOfKind(FileKind.HTML).size());
    counts.put("yaml", project.filesOfKind(FileKind.YAML).size());
    counts.put("css", project.filesOfKind(FileKind.CSS).size());
    counts.put("java", 0);
    return Map.copyOf(counts);
  }

  private record ResolvedRules(
    List<org.sonar.plugins.javascript.bridge.EslintRule> jsRuntimeRules,
    List<org.sonar.plugins.javascript.bridge.StylelintRule> cssRuntimeRules,
    Map<String, String> cssRuleKeyByStylelintKey,
    Map<String, RuleDefinition> ruleByRawKey
  ) {}
}
