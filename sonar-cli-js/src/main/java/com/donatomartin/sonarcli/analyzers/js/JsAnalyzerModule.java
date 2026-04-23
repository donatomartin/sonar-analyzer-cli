package com.donatomartin.sonarcli.analyzers.js;

import com.donatomartin.sonarcli.analyzers.js.catalog.JsRuleCatalogLoader;
import com.donatomartin.sonarcli.analyzers.js.model.JsRuleEntry;
import com.donatomartin.sonarcli.analyzers.js.runtime.JsAnalyzerRuntime;
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

public final class JsAnalyzerModule implements AnalyzerModule {

  private final List<JsRuleEntry> entries;
  private final Map<String, JsRuleEntry> byFamilyAndKey;

  public JsAnalyzerModule() {
    this.entries = new JsRuleCatalogLoader().load();
    this.byFamilyAndKey = entries.stream().collect(
      LinkedHashMap::new,
      (map, entry) -> map.put(key(entry.definition()), entry),
      Map::putAll
    );
  }

  @Override
  public String id() {
    return "js";
  }

  @Override
  public String displayName() {
    return "SonarJS";
  }

  @Override
  public Set<FileKind> supportedFileKinds() {
    return Set.of(FileKind.JS_TS, FileKind.HTML, FileKind.YAML, FileKind.CSS);
  }

  @Override
  public List<RuleDefinition> loadRules() {
    return entries.stream().map(JsRuleEntry::definition).toList();
  }

  @Override
  public AnalyzerResult analyzeProject(DiscoveredProject project, SelectedRuleSet selectedRules, AnalysisParameters parameters)
    throws Exception {
    try (var runtime = new JsAnalyzerRuntime(parameters)) {
      return runtime.analyzeProject(project, selectedEntries(selectedRules));
    }
  }

  @Override
  public AnalyzerResult analyzeFile(
    DiscoveredProject project,
    ScannedFile file,
    SelectedRuleSet selectedRules,
    AnalysisParameters parameters
  ) throws Exception {
    try (var runtime = new JsAnalyzerRuntime(parameters)) {
      return runtime.analyzeFile(project, file, selectedEntries(selectedRules));
    }
  }

  @Override
  public List<String> doctor(DiscoveredProject project, AnalysisParameters parameters) throws Exception {
    try (var runtime = new JsAnalyzerRuntime(parameters)) {
      return runtime.doctor(project);
    }
  }

  private List<JsRuleEntry> selectedEntries(SelectedRuleSet selectedRules) {
    return selectedRules.forAnalyzer(id()).stream()
      .map(selectedRule -> toSelectedEntry(selectedRule, byFamilyAndKey.get(key(selectedRule))))
      .filter(java.util.Objects::nonNull)
      .toList();
  }

  private static JsRuleEntry toSelectedEntry(RuleDefinition selectedRule, JsRuleEntry entry) {
    if (entry == null) {
      return null;
    }
    return new JsRuleEntry(selectedRule, entry.jsRuntimeRules(), entry.cssRuntimeRule());
  }

  private static String key(RuleDefinition definition) {
    return definition.family() + ":" + definition.rawKey();
  }
}
