package com.donatomartin.sonarcli.analyzers.java;

import com.donatomartin.sonarcli.analyzers.java.catalog.JavaRuleCatalogLoader;
import com.donatomartin.sonarcli.analyzers.java.catalog.JavaRuleEntry;
import com.donatomartin.sonarcli.analyzers.java.runtime.JavaAnalyzerRuntime;
import com.donatomartin.sonarcli.core.catalog.SelectedRuleSet;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.runtime.AnalysisParameters;
import com.donatomartin.sonarcli.core.runtime.AnalyzerModule;
import com.donatomartin.sonarcli.core.runtime.AnalyzerResult;
import com.donatomartin.sonarcli.core.scan.DiscoveredProject;
import com.donatomartin.sonarcli.core.scan.FileKind;
import com.donatomartin.sonarcli.core.scan.ScannedFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JavaAnalyzerModule implements AnalyzerModule {

  private final List<JavaRuleEntry> entries;
  private final Map<String, JavaRuleEntry> byFamilyAndKey;

  public JavaAnalyzerModule() {
    this.entries = new JavaRuleCatalogLoader().load();
    this.byFamilyAndKey = entries.stream().collect(
      LinkedHashMap::new,
      (map, entry) -> map.put(key(entry.definition()), entry),
      Map::putAll
    );
  }

  @Override
  public String id() {
    return "java";
  }

  @Override
  public String displayName() {
    return "SonarJava";
  }

  @Override
  public Set<FileKind> supportedFileKinds() {
    return Set.of(FileKind.JAVA);
  }

  @Override
  public List<RuleDefinition> loadRules() {
    return entries.stream().map(JavaRuleEntry::definition).toList();
  }

  @Override
  public AnalyzerResult analyzeProject(DiscoveredProject project, SelectedRuleSet selectedRules, AnalysisParameters parameters)
    throws Exception {
    return new JavaAnalyzerRuntime().analyzeProject(project, selectedEntries(selectedRules), parameters);
  }

  @Override
  public AnalyzerResult analyzeFile(
    DiscoveredProject project,
    ScannedFile file,
    SelectedRuleSet selectedRules,
    AnalysisParameters parameters
  ) throws Exception {
    return new JavaAnalyzerRuntime().analyzeFile(project, file, selectedEntries(selectedRules), parameters);
  }

  @Override
  public List<String> doctor(DiscoveredProject project, AnalysisParameters parameters) {
    return new JavaAnalyzerRuntime().doctor(project, parameters);
  }

  private List<JavaRuleEntry> selectedEntries(SelectedRuleSet selectedRules) {
    return selectedRules.forAnalyzer(id()).stream()
      .map(selectedRule -> toSelectedEntry(selectedRule, byFamilyAndKey.get(key(selectedRule))))
      .filter(java.util.Objects::nonNull)
      .toList();
  }

  private static JavaRuleEntry toSelectedEntry(RuleDefinition selectedRule, JavaRuleEntry entry) {
    if (entry == null) {
      return null;
    }
    return new JavaRuleEntry(selectedRule, entry.runtimeChecks());
  }

  private static String key(RuleDefinition definition) {
    return definition.family() + ":" + definition.rawKey();
  }
}
