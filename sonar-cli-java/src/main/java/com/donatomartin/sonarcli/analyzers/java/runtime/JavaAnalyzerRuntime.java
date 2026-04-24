package com.donatomartin.sonarcli.analyzers.java.runtime;

import com.donatomartin.sonarcli.analyzers.java.catalog.JavaRuleEntry;
import com.donatomartin.sonarcli.analyzers.java.catalog.JavaRuntimeCheck;
import com.donatomartin.sonarcli.core.model.IssueLocationRecord;
import com.donatomartin.sonarcli.core.model.IssueRecord;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.model.SourceType;
import com.donatomartin.sonarcli.core.runtime.AnalysisParameters;
import com.donatomartin.sonarcli.core.runtime.AnalyzerResult;
import com.donatomartin.sonarcli.core.scan.DiscoveredProject;
import com.donatomartin.sonarcli.core.scan.FileKind;
import com.donatomartin.sonarcli.core.scan.ScannedFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarProduct;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.fs.TextPointer;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.cache.ReadCache;
import org.sonar.api.batch.sensor.cache.WriteCache;
import org.sonar.api.batch.sensor.code.NewSignificantCode;
import org.sonar.api.batch.sensor.coverage.NewCoverage;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.error.NewAnalysisError;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.batch.sensor.issue.NewMessageFormatting;
import org.sonar.api.batch.sensor.issue.fix.NewInputFileEdit;
import org.sonar.api.batch.sensor.issue.fix.NewQuickFix;
import org.sonar.api.batch.sensor.issue.fix.NewTextEdit;
import org.sonar.api.batch.sensor.measure.NewMeasure;
import org.sonar.api.batch.sensor.rule.NewAdHocRule;
import org.sonar.api.batch.sensor.symbol.NewSymbol;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scanner.fs.InputProject;
import org.sonar.api.utils.Version;
import org.sonar.java.AnalysisWarningsWrapper;
import org.sonar.java.DefaultJavaResourceLocator;
import org.sonar.java.GeneratedCheckList;
import org.sonar.java.JavaFrontend;
import org.sonar.java.Measurer;
import org.sonar.java.SonarComponents;
import org.sonar.java.classpath.ClasspathForMain;
import org.sonar.java.classpath.ClasspathForTest;
import org.sonar.java.filters.PostAnalysisIssueFilter;
import org.sonar.java.model.JavaVersionImpl;
import org.sonar.java.telemetry.NoOpTelemetry;
import org.sonar.plugins.java.api.JavaCheck;
import org.sonar.plugins.java.api.JavaVersion;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

public final class JavaAnalyzerRuntime {

  public static final String ANALYZER_VERSION = "8.20.0.40630";
  private static final List<String> MAIN_BINARY_SUFFIXES = List.of(
    "target/classes",
    "build/classes/java/main"
  );
  private static final List<String> TEST_BINARY_SUFFIXES = List.of(
    "target/test-classes",
    "build/classes/java/test"
  );
  private static final Set<String> LIBRARY_DIRECTORY_NAMES = Set.of("dependency", "dependencies", "lib", "libs");

  public AnalyzerResult analyzeProject(DiscoveredProject project, List<JavaRuleEntry> selectedRules, AnalysisParameters parameters)
    throws IOException {
    return analyze(project, project.filesOfKind(FileKind.JAVA), selectedRules, parameters);
  }

  public AnalyzerResult analyzeFile(
    DiscoveredProject project,
    ScannedFile file,
    List<JavaRuleEntry> selectedRules,
    AnalysisParameters parameters
  ) throws IOException {
    return analyze(project, List.of(file), selectedRules, parameters);
  }

  public List<String> doctor(DiscoveredProject project, AnalysisParameters parameters) {
    var resolution = resolveInputs(project, parameters);
    var lines = new ArrayList<String>();
    lines.add("Analyzer version: " + ANALYZER_VERSION);
    lines.add("Java runtime: " + Runtime.version());
    lines.add("Detected java files: " + project.filesOfKind(FileKind.JAVA).size());
    addPathSummary(lines, project.baseDir(), "Configured sonar.java.binaries", parameters.javaBinaries());
    addPathSummary(lines, project.baseDir(), "Resolved sonar.java.binaries", resolution.mainBinaries());
    addPathSummary(lines, project.baseDir(), "Configured sonar.java.libraries", parameters.javaLibraries());
    addPathSummary(lines, project.baseDir(), "Resolved sonar.java.libraries", resolution.mainLibraries());
    addPathSummary(lines, project.baseDir(), "Configured sonar.java.test.binaries", parameters.javaTestBinaries());
    addPathSummary(lines, project.baseDir(), "Resolved sonar.java.test.binaries", resolution.testBinaries());
    addPathSummary(lines, project.baseDir(), "Configured sonar.java.test.libraries", parameters.javaTestLibraries());
    addPathSummary(lines, project.baseDir(), "Resolved sonar.java.test.libraries", resolution.testLibraries());
    lines.add("Configured sonar.java.source: " + (parameters.javaSourceVersion() == null ? "<auto>" : parameters.javaSourceVersion()));
    if (parameters.javaJdkHome() != null) {
      lines.add("Configured sonar.java.jdkHome: " + parameters.javaJdkHome());
    }
    if (!resolution.doctorWarnings().isEmpty()) {
      lines.add("Warnings:");
      resolution.doctorWarnings().forEach(warning -> lines.add("  - " + warning));
    }
    if (!resolution.remediationHints().isEmpty()) {
      lines.add("How to fix it:");
      resolution.remediationHints().forEach(hint -> lines.add("  - " + hint));
    }
    return List.copyOf(lines);
  }

  private AnalyzerResult analyze(
    DiscoveredProject project,
    List<ScannedFile> javaFiles,
    List<JavaRuleEntry> selectedRules,
    AnalysisParameters parameters
  ) throws IOException {
    if (javaFiles.isEmpty() || selectedRules.isEmpty()) {
      return new AnalyzerResult("java", ANALYZER_VERSION, List.of(), List.of(), 0, project.fileCountsByKind());
    }

    var inputs = new ArrayList<SimpleInputFile>();
    for (ScannedFile file : javaFiles) {
      inputs.add(new SimpleInputFile(project.baseDir(), file));
    }
    var resolvedInputs = resolveInputs(project, parameters);
    var fileSystem = new SimpleFileSystem(project.baseDir(), inputs);
    var configuration = new MapBackedConfiguration(buildConfiguration(parameters, resolvedInputs));
    var activeRules = new SimpleActiveRules(selectedRules);
    var sensorContext = new SimpleSensorContext(configuration, fileSystem, activeRules, project.baseDir());
    var capturedWarnings = new ArrayList<String>();
    AnalysisWarnings analysisWarnings = warning -> {
      if (!capturedWarnings.contains(warning)) {
        capturedWarnings.add(warning);
      }
    };

    var classpathForMain = new CapturingClasspathForMain(configuration, fileSystem, new AnalysisWarningsWrapper(analysisWarnings));
    var classpathForTest = new ClasspathForTest(configuration, fileSystem);
    var sonarComponents = new SonarComponents(
      new NoOpFileLinesContextFactory(),
      fileSystem,
      classpathForMain,
      classpathForTest,
      new CheckFactory(activeRules),
      activeRules
    );

    sonarComponents.registerMainChecks(
      GeneratedCheckList.REPOSITORY_KEY,
      selectedRules.stream()
        .flatMap(entry -> entry.runtimeChecks().stream())
        .filter(runtimeCheck -> runtimeCheck.sourceType() == SourceType.MAIN)
        .map(JavaRuntimeCheck::checkClass)
        .toList()
    );
    sonarComponents.registerTestChecks(
      GeneratedCheckList.REPOSITORY_KEY,
      selectedRules.stream()
        .flatMap(entry -> entry.runtimeChecks().stream())
        .filter(runtimeCheck -> runtimeCheck.sourceType() == SourceType.TEST)
        .map(JavaRuntimeCheck::checkClass)
        .toList()
    );
    sonarComponents.setSensorContext(sensorContext);

    JavaVersion javaVersion = parameters.javaSourceVersion() == null || parameters.javaSourceVersion().isBlank()
      ? new JavaVersionImpl()
      : JavaVersionImpl.fromString(parameters.javaSourceVersion(), Boolean.toString(parameters.javaEnablePreview()));

    var resourceLocator = new DefaultJavaResourceLocator(classpathForMain, classpathForTest);
    var frontend = new JavaFrontend(
      javaVersion,
      sonarComponents,
      new Measurer(sensorContext, new NoOpNoSonarFilter()),
      new NoOpTelemetry(),
      resourceLocator,
      new PostAnalysisIssueFilter(),
      sonarComponents.mainChecks().toArray(new JavaCheck[0])
    );
    frontend.scan(
      inputs.stream().filter(input -> input.type() == InputFile.Type.MAIN).map(input -> (InputFile) input).toList(),
      inputs.stream().filter(input -> input.type() == InputFile.Type.TEST).map(input -> (InputFile) input).toList(),
      List.of()
    );
    classpathForMain.logSuspiciousEmptyLibraries();
    classpathForTest.logSuspiciousEmptyLibraries();

    var rulesByKey = selectedRules.stream().collect(
      LinkedHashMap<String, RuleDefinition>::new,
      (map, entry) -> map.put(entry.definition().rawKey(), entry.definition()),
      Map::putAll
    );
    var issues = sensorContext.issues().stream()
      .map(issue -> toIssueRecord(project.baseDir(), issue, rulesByKey))
      .sorted(Comparator.comparing(IssueRecord::path).thenComparingInt(IssueRecord::startLine))
      .toList();
    var warnings = new ArrayList<String>();
    warnings.addAll(resolvedInputs.analysisWarnings());
    warnings.addAll(capturedWarnings);
    warnings.addAll(sensorContext.analysisErrors());
    return new AnalyzerResult("java", ANALYZER_VERSION, List.copyOf(warnings), issues, warnings.size(), javaFileCounts(javaFiles));
  }

  private static IssueRecord toIssueRecord(Path baseDir, CapturedIssue issue, Map<String, RuleDefinition> rulesByKey) {
    var rawKey = issue.ruleKey().rule();
    var rule = rulesByKey.get(rawKey);
    return new IssueRecord(
      "java",
      rule != null ? rule.family() : com.donatomartin.sonarcli.core.model.RuleFamily.JAVA,
      rawKey,
      rule != null ? rule.selector() : rawKey,
      relativePath(baseDir, issue.path()),
      issue.startLine(),
      issue.startOffset(),
      issue.endLine(),
      issue.endOffset(),
      issue.message(),
      rule != null ? rule.defaultSeverity() : null,
      rule != null ? rule.type() : null,
      issue.secondaryLocations().stream()
        .map(location -> new IssueLocationRecord(
          relativePath(baseDir, location.path()),
          location.startLine(),
          location.startOffset(),
          location.endLine(),
          location.endOffset(),
          location.message()
        ))
        .toList()
    );
  }

  private static String relativePath(Path baseDir, Path path) {
    try {
      return baseDir.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    } catch (IllegalArgumentException ignored) {
      return path.toString();
    }
  }

  private static Map<String, String> buildConfiguration(AnalysisParameters parameters, ResolvedJavaInputs resolvedInputs) {
    var values = new LinkedHashMap<String, String>();
    if (!resolvedInputs.mainBinaries().isEmpty()) {
      values.put("sonar.java.binaries", joinPaths(resolvedInputs.mainBinaries()));
    }
    if (!resolvedInputs.mainLibraries().isEmpty()) {
      values.put("sonar.java.libraries", joinPaths(resolvedInputs.mainLibraries()));
    }
    if (!resolvedInputs.testBinaries().isEmpty()) {
      values.put("sonar.java.test.binaries", joinPaths(resolvedInputs.testBinaries()));
    }
    if (!resolvedInputs.testLibraries().isEmpty()) {
      values.put("sonar.java.test.libraries", joinPaths(resolvedInputs.testLibraries()));
    }
    if (parameters.javaJdkHome() != null) {
      values.put("sonar.java.jdkHome", parameters.javaJdkHome().toAbsolutePath().normalize().toString());
    }
    if (parameters.javaSourceVersion() != null && !parameters.javaSourceVersion().isBlank()) {
      values.put(JavaVersion.SOURCE_VERSION, parameters.javaSourceVersion());
    }
    if (parameters.javaEnablePreview()) {
      values.put(JavaVersion.ENABLE_PREVIEW, "true");
    }
    values.put(SonarComponents.SONAR_FILE_BY_FILE, "true");
    return values;
  }

  private static String joinPaths(List<Path> paths) {
    return paths.stream().map(path -> path.toAbsolutePath().normalize().toString()).reduce((left, right) -> left + "," + right).orElse("");
  }

  private static void addPathSummary(List<String> lines, Path baseDir, String label, List<Path> paths) {
    lines.add(label + ": " + paths.size());
    for (Path path : paths.stream().limit(5).toList()) {
      lines.add("  - " + relativePath(baseDir, path));
    }
    if (paths.size() > 5) {
      lines.add("  - ...");
    }
  }

  private static ResolvedJavaInputs resolveInputs(DiscoveredProject project, AnalysisParameters parameters) {
    var mainBinaries = parameters.javaBinaries().isEmpty()
      ? discoverDirectories(project.baseDir(), MAIN_BINARY_SUFFIXES, JavaAnalyzerRuntime::containsClassFiles)
      : parameters.javaBinaries();
    var testBinaries = parameters.javaTestBinaries().isEmpty()
      ? discoverDirectories(project.baseDir(), TEST_BINARY_SUFFIXES, JavaAnalyzerRuntime::containsClassFiles)
      : parameters.javaTestBinaries();
    var mainLibraries = parameters.javaLibraries().isEmpty()
      ? discoverLibraries(project.baseDir())
      : parameters.javaLibraries();
    var testLibraries = parameters.javaTestLibraries().isEmpty() ? mainLibraries : parameters.javaTestLibraries();

    var analysisWarnings = new ArrayList<String>();
    var doctorWarnings = new ArrayList<String>();
    if (project.filesOfKind(FileKind.JAVA).size() > 1 && mainBinaries.isEmpty()) {
      analysisWarnings.add("Compiled classes were not found; local Java analysis may miss semantic issues until sonar.java.binaries is set.");
      doctorWarnings.add("No compiled main classes were resolved for sonar.java.binaries.");
    }
    if (!project.files().stream().filter(file -> file.kind() == FileKind.JAVA).filter(file -> file.sourceType() == SourceType.TEST).toList().isEmpty() && testBinaries.isEmpty()) {
      analysisWarnings.add("Test bytecode was not found; test-only Java rules may be less accurate until sonar.java.test.binaries is set.");
      doctorWarnings.add("No compiled test classes were resolved for sonar.java.test.binaries.");
    }
    if (mainLibraries.isEmpty()) {
      analysisWarnings.add("Dependencies/libraries were not resolved; Java analysis may be less precise until sonar.java.libraries is set.");
      doctorWarnings.add("No dependency jars were resolved for sonar.java.libraries.");
    }
    if (!project.files().stream().filter(file -> file.kind() == FileKind.JAVA).filter(file -> file.sourceType() == SourceType.TEST).toList().isEmpty() && testLibraries.isEmpty()) {
      doctorWarnings.add("No dependency jars were resolved for sonar.java.test.libraries.");
    }

    return new ResolvedJavaInputs(
      List.copyOf(mainBinaries),
      List.copyOf(mainLibraries),
      List.copyOf(testBinaries),
      List.copyOf(testLibraries),
      List.copyOf(analysisWarnings),
      List.copyOf(doctorWarnings),
      List.copyOf(remediationHints(project.baseDir(), mainBinaries.isEmpty(), testBinaries.isEmpty(), mainLibraries.isEmpty()))
    );
  }

  private static List<Path> discoverDirectories(Path baseDir, List<String> suffixes, java.util.function.Predicate<Path> contentsPredicate) {
    try (Stream<Path> paths = Files.walk(baseDir, 6)) {
      return paths
        .filter(Files::isDirectory)
        .filter(path -> matchesAnySuffix(baseDir, path, suffixes))
        .filter(contentsPredicate)
        .sorted()
        .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static List<Path> discoverLibraries(Path baseDir) {
    try (Stream<Path> paths = Files.walk(baseDir, 6)) {
      return paths
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
        .filter(path -> LIBRARY_DIRECTORY_NAMES.contains(path.getParent().getFileName().toString().toLowerCase(Locale.ROOT)))
        .sorted()
        .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static boolean matchesAnySuffix(Path baseDir, Path candidate, List<String> suffixes) {
    var relative = baseDir.toAbsolutePath().normalize().relativize(candidate.toAbsolutePath().normalize()).toString().replace('\\', '/');
    for (String suffix : suffixes) {
      if (relative.equals(suffix) || relative.endsWith("/" + suffix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsClassFiles(Path directory) {
    try (Stream<Path> files = Files.walk(directory, 2)) {
      return files.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class"));
    } catch (IOException e) {
      return false;
    }
  }

  private static List<String> remediationHints(
    Path baseDir,
    boolean needsMainBinaries,
    boolean needsTestBinaries,
    boolean needsLibraries
  ) {
    var hints = new LinkedHashSet<String>();
    if (Files.exists(baseDir.resolve("pom.xml")) || Files.exists(baseDir.resolve("mvnw"))) {
      if (needsMainBinaries || needsTestBinaries) {
        hints.add("Compile first with `mvn -q -DskipTests compile test-compile`, then re-run the CLI.");
      }
      if (needsLibraries) {
        hints.add(
          "If dependencies are unpacked locally, point `--java-libraries` (and optionally `--java-test-libraries`) at jars such as `target/dependency/*.jar`."
        );
      }
    } else if (
      Files.exists(baseDir.resolve("build.gradle")) ||
      Files.exists(baseDir.resolve("build.gradle.kts")) ||
      Files.exists(baseDir.resolve("gradlew"))
    ) {
      if (needsMainBinaries || needsTestBinaries) {
        hints.add("Compile first with `./gradlew classes testClasses`, then re-run the CLI.");
      }
      if (needsLibraries) {
        hints.add("Point `--java-libraries` at dependency jars or directories such as `build/dependencies`, `libs`, or `lib`.");
      }
    } else {
      if (needsMainBinaries) {
        hints.add("Pass compiled output directories with `--java-binaries` (for example `target/classes` or `build/classes/java/main`).");
      }
      if (needsTestBinaries) {
        hints.add("Pass compiled test output directories with `--java-test-binaries` when test sources are analyzed.");
      }
      if (needsLibraries) {
        hints.add("Pass dependency jars or directories with `--java-libraries` so semantic Java analysis can resolve external types.");
      }
    }
    if (needsMainBinaries || needsTestBinaries) {
      hints.add("If build outputs live outside the analyzed root, pass absolute paths to `--java-binaries` and `--java-test-binaries`.");
    }
    return List.copyOf(hints);
  }

  private static Map<String, Integer> javaFileCounts(List<ScannedFile> javaFiles) {
    var counts = new LinkedHashMap<String, Integer>();
    counts.put("js-ts", 0);
    counts.put("html", 0);
    counts.put("yaml", 0);
    counts.put("css", 0);
    counts.put("java", javaFiles.size());
    return Map.copyOf(counts);
  }

  private record ResolvedJavaInputs(
    List<Path> mainBinaries,
    List<Path> mainLibraries,
    List<Path> testBinaries,
    List<Path> testLibraries,
    List<String> analysisWarnings,
    List<String> doctorWarnings,
    List<String> remediationHints
  ) {}

  private static final class CapturingClasspathForMain extends ClasspathForMain {
    private CapturingClasspathForMain(Configuration configuration, FileSystem fileSystem, AnalysisWarningsWrapper analysisWarnings) {
      super(configuration, fileSystem, analysisWarnings);
    }

    @Override
    protected boolean isSonarLint() {
      return true;
    }
  }

  private static final class MapBackedConfiguration implements Configuration {
    private final Map<String, String> values;

    private MapBackedConfiguration(Map<String, String> values) {
      this.values = Map.copyOf(values);
    }

    @Override
    public Optional<String> get(String key) {
      return Optional.ofNullable(values.get(key));
    }

    @Override
    public boolean hasKey(String key) {
      return values.containsKey(key);
    }

    @Override
    public String[] getStringArray(String key) {
      var value = values.get(key);
      if (value == null || value.isBlank()) {
        return new String[0];
      }
      return value.split(",");
    }
  }

  private static final class SimpleSettings extends Settings {
    private final Map<String, String> values;

    private SimpleSettings(Map<String, String> values) {
      this.values = values;
    }

    @Override
    public boolean hasKey(String key) {
      return values.containsKey(key);
    }

    @Override
    public String getString(String key) {
      return values.get(key);
    }

    @Override
    public boolean getBoolean(String key) {
      return Boolean.parseBoolean(values.get(key));
    }

    @Override
    public int getInt(String key) {
      return Integer.parseInt(values.get(key));
    }

    @Override
    public long getLong(String key) {
      return Long.parseLong(values.get(key));
    }

    @Override
    public Date getDate(String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Date getDateTime(String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Float getFloat(String key) {
      return Float.parseFloat(values.get(key));
    }

    @Override
    public Double getDouble(String key) {
      return Double.parseDouble(values.get(key));
    }

    @Override
    public String[] getStringArray(String key) {
      var value = values.get(key);
      return value == null || value.isBlank() ? new String[0] : value.split(",");
    }

    @Override
    public String[] getStringLines(String key) {
      return getStringArray(key);
    }

    @Override
    public String[] getStringArrayBySeparator(String key, String separator) {
      var value = values.get(key);
      return value == null || value.isBlank() ? new String[0] : value.split(separator);
    }

    @Override
    public List<String> getKeysStartingWith(String prefix) {
      return values.keySet().stream().filter(key -> key.startsWith(prefix)).toList();
    }
  }

  private static final class NoOpFileLinesContextFactory implements FileLinesContextFactory {
    @Override
    public FileLinesContext createFor(InputFile inputFile) {
      return new FileLinesContext() {
        @Override
        public void setIntValue(String metricKey, int line, int value) {}

        @Override
        public void setStringValue(String metricKey, int line, String value) {}

        @Override
        public void save() {}
      };
    }
  }

  private static final class NoOpNoSonarFilter extends NoSonarFilter {
    @Override
    public NoSonarFilter noSonarInFile(InputFile inputFile, Set<Integer> noSonarLines) {
      return this;
    }
  }

  private static final class SimpleActiveRules implements ActiveRules {
    private final Map<RuleKey, ActiveRule> rules;

    private SimpleActiveRules(List<JavaRuleEntry> selectedRules) {
      var map = new LinkedHashMap<RuleKey, ActiveRule>();
      for (JavaRuleEntry entry : selectedRules) {
        RuleKey key = RuleKey.of(GeneratedCheckList.REPOSITORY_KEY, entry.definition().rawKey());
        map.put(key, new SimpleActiveRule(key, entry.definition().defaultSeverity(), "java"));
      }
      this.rules = Map.copyOf(map);
    }

    @Override
    public ActiveRule find(RuleKey ruleKey) {
      return rules.get(ruleKey);
    }

    @Override
    public Collection<ActiveRule> findAll() {
      return rules.values();
    }

    @Override
    public Collection<ActiveRule> findByRepository(String repository) {
      return rules.entrySet().stream()
        .filter(entry -> entry.getKey().repository().equals(repository))
        .map(Map.Entry::getValue)
        .toList();
    }

    @Override
    public Collection<ActiveRule> findByLanguage(String language) {
      return rules.values().stream().filter(rule -> language.equals(rule.language())).toList();
    }

    @Override
    public ActiveRule findByInternalKey(String repository, String internalKey) {
      return rules.values().stream()
        .filter(rule -> repository.equals(rule.ruleKey().repository()) && internalKey.equals(rule.internalKey()))
        .findFirst()
        .orElse(null);
    }
  }

  private record SimpleActiveRule(RuleKey ruleKey, String severity, String language) implements ActiveRule {
    @Override
    public String param(String key) {
      return null;
    }

    @Override
    public Map<String, String> params() {
      return Map.of();
    }

    @Override
    public String internalKey() {
      return ruleKey.rule();
    }

    @Override
    public String templateRuleKey() {
      return null;
    }

    @Override
    public String qpKey() {
      return "cli";
    }
  }

  private static final class SimpleFileSystem implements FileSystem {
    private final File baseDir;
    private final List<SimpleInputFile> files;
    private final FilePredicates predicates = new SimpleFilePredicates();

    private SimpleFileSystem(Path baseDir, List<SimpleInputFile> files) {
      this.baseDir = baseDir.toFile();
      this.files = files;
    }

    @Override
    public File baseDir() {
      return baseDir;
    }

    @Override
    public Charset encoding() {
      return StandardCharsets.UTF_8;
    }

    @Override
    public File workDir() {
      return new File(baseDir, ".sonar-work");
    }

    @Override
    public FilePredicates predicates() {
      return predicates;
    }

    @Override
    public InputFile inputFile(FilePredicate predicate) {
      return files.stream().filter(predicate::apply).findFirst().orElse(null);
    }

    @Override
    public org.sonar.api.batch.fs.InputDir inputDir(File file) {
      return null;
    }

    @Override
    public Iterable<InputFile> inputFiles(FilePredicate predicate) {
      return files.stream().filter(predicate::apply).map(file -> (InputFile) file).toList();
    }

    @Override
    public boolean hasFiles(FilePredicate predicate) {
      return files.stream().anyMatch(predicate::apply);
    }

    @Override
    public Iterable<File> files(FilePredicate predicate) {
      return files.stream().filter(predicate::apply).map(InputFile::file).toList();
    }

    @Override
    public SortedSet<String> languages() {
      return new TreeSet<>(Set.of("java"));
    }

    @Override
    public File resolvePath(String path) {
      return baseDir.toPath().resolve(path).normalize().toFile();
    }

    private final class SimpleFilePredicates implements FilePredicates {
      @Override
      public FilePredicate all() {
        return inputFile -> true;
      }

      @Override
      public FilePredicate none() {
        return inputFile -> false;
      }

      @Override
      public FilePredicate hasAbsolutePath(String absolutePath) {
        return inputFile -> inputFile.absolutePath().equals(absolutePath);
      }

      @Override
      public FilePredicate hasRelativePath(String relativePath) {
        return inputFile -> inputFile.relativePath().equals(relativePath);
      }

      @Override
      public FilePredicate hasFilename(String filename) {
        return inputFile -> inputFile.filename().equals(filename);
      }

      @Override
      public FilePredicate hasExtension(String extension) {
        return inputFile -> inputFile.filename().endsWith(extension.startsWith(".") ? extension : "." + extension);
      }

      @Override
      public FilePredicate hasURI(URI uri) {
        return inputFile -> inputFile.uri().equals(uri);
      }

      @Override
      public FilePredicate matchesPathPattern(String pathPattern) {
        return inputFile -> inputFile.relativePath().matches(pathPattern.replace("**", ".*").replace("*", "[^/]*"));
      }

      @Override
      public FilePredicate matchesPathPatterns(String[] pathPatterns) {
        return or(java.util.Arrays.stream(pathPatterns).map(this::matchesPathPattern).toList());
      }

      @Override
      public FilePredicate doesNotMatchPathPattern(String pathPattern) {
        return not(matchesPathPattern(pathPattern));
      }

      @Override
      public FilePredicate doesNotMatchPathPatterns(String[] pathPatterns) {
        return not(matchesPathPatterns(pathPatterns));
      }

      @Override
      public FilePredicate hasPath(String path) {
        return inputFile -> inputFile.relativePath().equals(path) || inputFile.absolutePath().equals(path);
      }

      @Override
      public FilePredicate is(File file) {
        return inputFile -> inputFile.file().equals(file);
      }

      @Override
      public FilePredicate hasLanguage(String language) {
        return inputFile -> language.equals(inputFile.language());
      }

      @Override
      public FilePredicate hasLanguages(Collection<String> languages) {
        return inputFile -> languages.contains(inputFile.language());
      }

      @Override
      public FilePredicate hasLanguages(String... languages) {
        return hasLanguages(List.of(languages));
      }

      @Override
      public FilePredicate hasType(InputFile.Type type) {
        return inputFile -> inputFile.type() == type;
      }

      @Override
      public FilePredicate not(FilePredicate predicate) {
        return inputFile -> !predicate.apply(inputFile);
      }

      @Override
      public FilePredicate or(Collection<FilePredicate> predicates) {
        return inputFile -> predicates.stream().anyMatch(predicate -> predicate.apply(inputFile));
      }

      @Override
      public FilePredicate or(FilePredicate... predicates) {
        return or(List.of(predicates));
      }

      @Override
      public FilePredicate or(FilePredicate left, FilePredicate right) {
        return inputFile -> left.apply(inputFile) || right.apply(inputFile);
      }

      @Override
      public FilePredicate and(Collection<FilePredicate> predicates) {
        return inputFile -> predicates.stream().allMatch(predicate -> predicate.apply(inputFile));
      }

      @Override
      public FilePredicate and(FilePredicate... predicates) {
        return and(List.of(predicates));
      }

      @Override
      public FilePredicate and(FilePredicate left, FilePredicate right) {
        return inputFile -> left.apply(inputFile) && right.apply(inputFile);
      }

      @Override
      public FilePredicate hasStatus(InputFile.Status status) {
        return inputFile -> inputFile.status() == status;
      }

      @Override
      public FilePredicate hasAnyStatus() {
        return inputFile -> true;
      }
    }
  }

  private static final class SimpleInputFile implements InputFile {
    private final Path baseDir;
    private final ScannedFile scannedFile;
    private final List<String> lines;
    private final String contents;

    private SimpleInputFile(Path baseDir, ScannedFile scannedFile) throws IOException {
      this.baseDir = baseDir;
      this.scannedFile = scannedFile;
      this.contents = Files.readString(scannedFile.path());
      this.lines = List.of(contents.split("\\R", -1));
    }

    @Override
    public String key() {
      return relativePath();
    }

    @Override
    public boolean isFile() {
      return true;
    }

    @Override
    public String relativePath() {
      return scannedFile.relativePath();
    }

    @Override
    public String absolutePath() {
      return scannedFile.path().toString();
    }

    @Override
    public File file() {
      return scannedFile.path().toFile();
    }

    @Override
    public Path path() {
      return scannedFile.path();
    }

    @Override
    public URI uri() {
      return scannedFile.path().toUri();
    }

    @Override
    public String filename() {
      return scannedFile.path().getFileName().toString();
    }

    @Override
    public String language() {
      return "java";
    }

    @Override
    public Type type() {
      return scannedFile.sourceType() == SourceType.TEST ? Type.TEST : Type.MAIN;
    }

    @Override
    public InputStream inputStream() {
      return new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String contents() {
      return contents;
    }

    @Override
    public Status status() {
      return Status.ADDED;
    }

    @Override
    public int lines() {
      return lines.size();
    }

    @Override
    public boolean isEmpty() {
      return contents.isBlank();
    }

    @Override
    public TextPointer newPointer(int line, int lineOffset) {
      return new SimpleTextPointer(line, lineOffset);
    }

    @Override
    public TextRange newRange(TextPointer start, TextPointer end) {
      return new SimpleTextRange(start, end);
    }

    @Override
    public TextRange newRange(int startLine, int startLineOffset, int endLine, int endLineOffset) {
      return new SimpleTextRange(newPointer(startLine, startLineOffset), newPointer(endLine, endLineOffset));
    }

    @Override
    public TextRange selectLine(int line) {
      var content = line > 0 && line <= lines.size() ? lines.get(line - 1) : "";
      return newRange(line, 0, line, content.length());
    }

    @Override
    public Charset charset() {
      return StandardCharsets.UTF_8;
    }

    @Override
    public String md5Hash() {
      try {
        byte[] digest = MessageDigest.getInstance("MD5").digest(contents.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public String toString() {
      return relativePath();
    }
  }

  private record SimpleTextPointer(int line, int lineOffset) implements TextPointer {
    @Override
    public int compareTo(TextPointer other) {
      int byLine = Integer.compare(line, other.line());
      return byLine != 0 ? byLine : Integer.compare(lineOffset, other.lineOffset());
    }
  }

  private record SimpleTextRange(TextPointer start, TextPointer end) implements TextRange {
    @Override
    public boolean overlap(TextRange other) {
      return start.compareTo(other.end()) <= 0 && end.compareTo(other.start()) >= 0;
    }
  }

  private static final class SimpleProject implements InputProject, InputModule {
    private final String key;

    private SimpleProject(Path baseDir) {
      this.key = baseDir.getFileName() == null ? baseDir.toString() : baseDir.getFileName().toString();
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public boolean isFile() {
      return false;
    }
  }

  private static final class SimpleSensorContext implements SensorContext {
    private final MapBackedConfiguration configuration;
    private final SimpleFileSystem fileSystem;
    private final ActiveRules activeRules;
    private final SimpleSettings settings;
    private final SimpleProject project;
    private final List<CapturedIssue> issues = new ArrayList<>();
    private final List<String> analysisErrors = new ArrayList<>();
    private final SonarRuntime runtime = new SimpleSonarLintRuntime();

    private SimpleSensorContext(
      MapBackedConfiguration configuration,
      SimpleFileSystem fileSystem,
      ActiveRules activeRules,
      Path baseDir
    ) {
      this.configuration = configuration;
      this.fileSystem = fileSystem;
      this.activeRules = activeRules;
      this.settings = new SimpleSettings(configuration.values);
      this.project = new SimpleProject(baseDir);
    }

    private List<CapturedIssue> issues() {
      return List.copyOf(issues);
    }

    private List<String> analysisErrors() {
      return List.copyOf(analysisErrors);
    }

    @Override
    public Settings settings() {
      return settings;
    }

    @Override
    public Configuration config() {
      return configuration;
    }

    @Override
    public boolean canSkipUnchangedFiles() {
      return false;
    }

    @Override
    public FileSystem fileSystem() {
      return fileSystem;
    }

    @Override
    public ActiveRules activeRules() {
      return activeRules;
    }

    @Override
    public InputModule module() {
      return project;
    }

    @Override
    public InputProject project() {
      return project;
    }

    @Override
    public Version getSonarQubeVersion() {
      return Version.create(10, 0);
    }

    @Override
    public SonarRuntime runtime() {
      return runtime;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public <G extends Serializable> NewMeasure<G> newMeasure() {
      return new NoOpNewMeasure<>();
    }

    @Override
    public NewIssue newIssue() {
      return new SimpleNewIssue(this);
    }

    @Override
    public org.sonar.api.batch.sensor.issue.NewExternalIssue newExternalIssue() {
      throw new UnsupportedOperationException();
    }

    @Override
    public NewAdHocRule newAdHocRule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public NewHighlighting newHighlighting() {
      return new NoOpHighlighting();
    }

    @Override
    public NewSymbolTable newSymbolTable() {
      return new NoOpSymbolTable();
    }

    @Override
    public NewCoverage newCoverage() {
      return new NoOpCoverage();
    }

    @Override
    public NewCpdTokens newCpdTokens() {
      return new NoOpCpdTokens();
    }

    @Override
    public NewAnalysisError newAnalysisError() {
      return new SimpleNewAnalysisError(analysisErrors);
    }

    @Override
    public NewSignificantCode newSignificantCode() {
      return new NoOpSignificantCode();
    }

    @Override
    public void addContextProperty(String key, String value) {}

    @Override
    public void markForPublishing(InputFile inputFile) {}

    @Override
    public void markAsUnchanged(InputFile inputFile) {}

    @Override
    public WriteCache nextCache() {
      return new NoOpWriteCache();
    }

    @Override
    public ReadCache previousCache() {
      return new NoOpReadCache();
    }

    @Override
    public boolean isCacheEnabled() {
      return false;
    }

    @Override
    public void addTelemetryProperty(String key, String value) {}

    @Override
    public void addAnalysisData(String key, String name, InputStream stream) {}

    @Override
    public boolean isFeatureAvailable(String feature) {
      return false;
    }
  }

  private static final class SimpleSonarLintRuntime implements SonarLintRuntime {
    @Override
    public Version getSonarLintPluginApiVersion() {
      return Version.parse("10.13");
    }

    @Override
    public long getClientPid() {
      return ProcessHandle.current().pid();
    }

    @Override
    public Version getApiVersion() {
      return Version.parse("13.4");
    }

    @Override
    public SonarProduct getProduct() {
      return SonarProduct.SONARLINT;
    }

    @Override
    public SonarQubeSide getSonarQubeSide() {
      return SonarQubeSide.SCANNER;
    }

    @Override
    public SonarEdition getEdition() {
      return SonarEdition.COMMUNITY;
    }
  }

  private static final class SimpleNewIssue implements NewIssue {
    private final SimpleSensorContext context;
    private RuleKey ruleKey;
    private Double gap;
    private CapturedLocation primaryLocation;
    private final List<CapturedLocation> secondaryLocations = new ArrayList<>();

    private SimpleNewIssue(SimpleSensorContext context) {
      this.context = context;
    }

    @Override
    public NewIssue forRule(RuleKey ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    @Override
    public NewIssue gap(Double gap) {
      this.gap = gap;
      return this;
    }

    @Override
    public NewIssue overrideSeverity(org.sonar.api.batch.rule.Severity severity) {
      return this;
    }

    @Override
    public NewIssue overrideImpact(SoftwareQuality softwareQuality, org.sonar.api.issue.impact.Severity severity) {
      return this;
    }

    @Override
    public NewIssue at(NewIssueLocation primaryLocation) {
      this.primaryLocation = ((SimpleNewIssueLocation) primaryLocation).toCapturedLocation();
      return this;
    }

    @Override
    public NewIssue addLocation(NewIssueLocation secondaryLocation) {
      this.secondaryLocations.add(((SimpleNewIssueLocation) secondaryLocation).toCapturedLocation());
      return this;
    }

    @Override
    public NewIssue setQuickFixAvailable(boolean quickFixAvailable) {
      return this;
    }

    @Override
    public NewIssue addFlow(Iterable<NewIssueLocation> flowLocations) {
      for (NewIssueLocation location : flowLocations) {
        addLocation(location);
      }
      return this;
    }

    @Override
    public NewIssue addFlow(Iterable<NewIssueLocation> flowLocations, FlowType flowType, String description) {
      return addFlow(flowLocations);
    }

    @Override
    public NewIssueLocation newLocation() {
      return new SimpleNewIssueLocation();
    }

    @Override
    public NewQuickFix newQuickFix() {
      return new NoOpQuickFix();
    }

    @Override
    public NewIssue addQuickFix(NewQuickFix newQuickFix) {
      return this;
    }

    @Override
    public void save() {
      if (ruleKey == null || primaryLocation == null) {
        return;
      }
      context.issues.add(
        new CapturedIssue(
          ruleKey,
          primaryLocation.path(),
          primaryLocation.startLine(),
          primaryLocation.startOffset(),
          primaryLocation.endLine(),
          primaryLocation.endOffset(),
          primaryLocation.message(),
          List.copyOf(secondaryLocations)
        )
      );
    }

    @Override
    public NewIssue setRuleDescriptionContextKey(String contextKey) {
      return this;
    }

    @Override
    public NewIssue setCodeVariants(Iterable<String> codeVariants) {
      return this;
    }

    @Override
    public NewIssue addInternalTag(String tag) {
      return this;
    }

    @Override
    public NewIssue addInternalTags(Collection<String> tags) {
      return this;
    }

    @Override
    public NewIssue setInternalTags(Collection<String> tags) {
      return this;
    }
  }

  private static final class SimpleNewIssueLocation implements NewIssueLocation {
    private InputComponent component;
    private TextRange range;
    private String message;

    @Override
    public NewIssueLocation on(InputComponent component) {
      this.component = component;
      return this;
    }

    @Override
    public NewIssueLocation at(TextRange range) {
      this.range = range;
      return this;
    }

    @Override
    public NewIssueLocation message(String message) {
      this.message = message;
      return this;
    }

    @Override
    public NewIssueLocation message(String message, List<NewMessageFormatting> formattings) {
      return message(message);
    }

    @Override
    public NewMessageFormatting newMessageFormatting() {
      return new NoOpMessageFormatting();
    }

    private CapturedLocation toCapturedLocation() {
      Path path = component instanceof InputFile inputFile ? inputFile.path() : Path.of(component.key());
      if (range == null) {
        range = new SimpleTextRange(new SimpleTextPointer(1, 0), new SimpleTextPointer(1, 0));
      }
      return new CapturedLocation(
        path,
        range.start().line(),
        range.start().lineOffset(),
        range.end().line(),
        range.end().lineOffset(),
        message
      );
    }
  }

  private static final class SimpleNewAnalysisError implements NewAnalysisError {
    private final List<String> errors;
    private InputFile file;
    private String message;
    private TextPointer pointer;

    private SimpleNewAnalysisError(List<String> errors) {
      this.errors = errors;
    }

    @Override
    public NewAnalysisError onFile(InputFile inputFile) {
      this.file = inputFile;
      return this;
    }

    @Override
    public NewAnalysisError message(String message) {
      this.message = message;
      return this;
    }

    @Override
    public NewAnalysisError at(TextPointer pointer) {
      this.pointer = pointer;
      return this;
    }

    @Override
    public void save() {
      if (file != null && message != null) {
        errors.add(file.relativePath() + ":" + (pointer == null ? 1 : pointer.line()) + " " + message);
      }
    }
  }

  private static final class NoOpNewMeasure<G extends Serializable> implements NewMeasure<G> {
    @Override
    public NewMeasure<G> on(InputComponent inputComponent) {
      return this;
    }

    @Override
    public NewMeasure<G> forMetric(Metric<G> metric) {
      return this;
    }

    @Override
    public NewMeasure<G> withValue(G value) {
      return this;
    }

    @Override
    public void save() {}
  }

  private static final class NoOpHighlighting implements NewHighlighting {
    @Override
    public NewHighlighting onFile(InputFile inputFile) {
      return this;
    }

    @Override
    public NewHighlighting highlight(TextRange textRange, org.sonar.api.batch.sensor.highlighting.TypeOfText typeOfText) {
      return this;
    }

    @Override
    public NewHighlighting highlight(int startLine, int startLineOffset, int endLine, int endLineOffset,
      org.sonar.api.batch.sensor.highlighting.TypeOfText typeOfText) {
      return this;
    }

    @Override
    public void save() {}
  }

  private static final class NoOpSymbolTable implements NewSymbolTable {
    @Override
    public NewSymbolTable onFile(InputFile inputFile) {
      return this;
    }

    @Override
    public NewSymbol newSymbol(TextRange range) {
      return new NoOpSymbol();
    }

    @Override
    public NewSymbol newSymbol(int startLine, int startLineOffset, int endLine, int endLineOffset) {
      return new NoOpSymbol();
    }

    @Override
    public void save() {}
  }

  private static final class NoOpSymbol implements NewSymbol {
    @Override
    public NewSymbol newReference(TextRange range) {
      return this;
    }

    @Override
    public NewSymbol newReference(int startLine, int startLineOffset, int endLine, int endLineOffset) {
      return this;
    }
  }

  private static final class NoOpCoverage implements NewCoverage {
    @Override
    public NewCoverage onFile(InputFile inputFile) {
      return this;
    }

    @Override
    public NewCoverage lineHits(int line, int hits) {
      return this;
    }

    @Override
    public NewCoverage conditions(int line, int conditions, int coveredConditions) {
      return this;
    }

    @Override
    public void save() {}
  }

  private static final class NoOpCpdTokens implements NewCpdTokens {
    @Override
    public NewCpdTokens onFile(InputFile inputFile) {
      return this;
    }

    @Override
    public NewCpdTokens addToken(TextRange range, String image) {
      return this;
    }

    @Override
    public NewCpdTokens addToken(int startLine, int startLineOffset, int endLine, int endLineOffset, String image) {
      return this;
    }

    @Override
    public void save() {}
  }

  private static final class NoOpSignificantCode implements NewSignificantCode {
    @Override
    public NewSignificantCode onFile(InputFile inputFile) {
      return this;
    }

    @Override
    public NewSignificantCode addRange(TextRange textRange) {
      return this;
    }

    @Override
    public void save() {}
  }

  private static final class NoOpWriteCache implements WriteCache {
    @Override
    public void write(String key, InputStream stream) {}

    @Override
    public void write(String key, byte[] data) {}

    @Override
    public void copyFromPrevious(String key) {}
  }

  private static final class NoOpReadCache implements ReadCache {
    @Override
    public InputStream read(String key) {
      return null;
    }

    @Override
    public boolean contains(String key) {
      return false;
    }
  }

  private static final class NoOpQuickFix implements NewQuickFix {
    @Override
    public NewQuickFix message(String message) {
      return this;
    }

    @Override
    public NewInputFileEdit newInputFileEdit() {
      return new NoOpInputFileEdit();
    }

    @Override
    public NewQuickFix addInputFileEdit(NewInputFileEdit newInputFileEdit) {
      return this;
    }
  }

  private static final class NoOpMessageFormatting implements NewMessageFormatting {
    @Override
    public NewMessageFormatting start(int start) {
      return this;
    }

    @Override
    public NewMessageFormatting end(int end) {
      return this;
    }

    @Override
    public NewMessageFormatting type(org.sonar.api.batch.sensor.issue.MessageFormatting.Type type) {
      return this;
    }
  }

  private static final class NoOpInputFileEdit implements NewInputFileEdit {
    @Override
    public NewInputFileEdit on(InputFile inputFile) {
      return this;
    }

    @Override
    public NewTextEdit newTextEdit() {
      return new NoOpTextEdit();
    }

    @Override
    public NewInputFileEdit addTextEdit(NewTextEdit newTextEdit) {
      return this;
    }
  }

  private static final class NoOpTextEdit implements NewTextEdit {
    @Override
    public NewTextEdit at(TextRange range) {
      return this;
    }

    @Override
    public NewTextEdit withNewText(String newText) {
      return this;
    }
  }

  private record CapturedIssue(
    RuleKey ruleKey,
    Path path,
    int startLine,
    int startOffset,
    int endLine,
    int endOffset,
    String message,
    List<CapturedLocation> secondaryLocations
  ) {}

  private record CapturedLocation(
    Path path,
    int startLine,
    int startOffset,
    int endLine,
    int endOffset,
    String message
  ) {}
}
