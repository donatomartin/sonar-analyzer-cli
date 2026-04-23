package com.donatomartin.sonarcli.analyzers.js.runtime;

import com.donatomartin.sonarcli.core.runtime.AnalysisParameters;
import com.donatomartin.sonarcli.core.scan.DiscoveredProject;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.plugins.javascript.api.AnalysisMode;
import org.sonar.plugins.javascript.bridge.AnalysisConfiguration;

public final class JsAnalysisConfigurationAdapter implements AnalysisConfiguration {

  private static final List<String> JS_EXTENSIONS = List.of(".js", ".jsx", ".mjs", ".cjs");
  private static final List<String> TS_EXTENSIONS = List.of(".ts", ".tsx", ".mts", ".cts");
  private static final List<String> CSS_EXTENSIONS = List.of(".css", ".scss", ".sass", ".less");

  private final AnalysisParameters parameters;
  private final DiscoveredProject project;

  public JsAnalysisConfigurationAdapter(AnalysisParameters parameters, DiscoveredProject project) {
    this.parameters = parameters;
    this.project = project;
  }

  @Override
  public boolean isSonarLint() {
    return true;
  }

  @Override
  public boolean allowTsParserJsFiles() {
    return true;
  }

  @Override
  public AnalysisMode getAnalysisMode() {
    return AnalysisMode.DEFAULT;
  }

  @Override
  public boolean ignoreHeaderComments() {
    return true;
  }

  @Override
  public long getMaxFileSizeProperty() {
    return parameters.maxFileSizeBytes();
  }

  @Override
  public int getTypeCheckingLimit() {
    return parameters.maxFilesForTypeChecking();
  }

  @Override
  public List<String> getEnvironments() {
    return parameters.environments();
  }

  @Override
  public List<String> getGlobals() {
    return parameters.globals();
  }

  @Override
  public List<String> getTsExtensions() {
    return TS_EXTENSIONS;
  }

  @Override
  public List<String> getJsExtensions() {
    return JS_EXTENSIONS;
  }

  @Override
  public List<String> getCssExtensions() {
    return CSS_EXTENSIONS;
  }

  @Override
  public Set<String> getTsConfigPaths() {
    return project.tsconfigPaths().stream().map(Path::toString).collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public List<String> getJsTsExcludedPaths() {
    return parameters.excludes();
  }

  @Override
  public boolean shouldDetectBundles() {
    return false;
  }

  @Override
  public boolean canAccessFileSystem() {
    return true;
  }

  @Override
  public List<String> getSources() {
    return List.of(".");
  }

  @Override
  public List<String> getInclusions() {
    return parameters.includes();
  }

  @Override
  public List<String> getExclusions() {
    return parameters.excludes();
  }

  @Override
  public List<String> getTests() {
    return parameters.tests();
  }

  @Override
  public List<String> getTestInclusions() {
    return parameters.tests();
  }

  @Override
  public List<String> getTestExclusions() {
    return List.of();
  }
}
