package com.donatomartin.sonarcli.core.runtime;

import com.donatomartin.sonarcli.core.catalog.SelectedRuleSet;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.scan.DiscoveredProject;
import com.donatomartin.sonarcli.core.scan.FileKind;
import com.donatomartin.sonarcli.core.scan.ScannedFile;
import java.util.List;
import java.util.Set;

public interface AnalyzerModule {
  String id();

  String displayName();

  Set<FileKind> supportedFileKinds();

  List<RuleDefinition> loadRules();

  AnalyzerResult analyzeProject(
    DiscoveredProject project,
    SelectedRuleSet selectedRules,
    AnalysisParameters parameters
  ) throws Exception;

  AnalyzerResult analyzeFile(
    DiscoveredProject project,
    ScannedFile file,
    SelectedRuleSet selectedRules,
    AnalysisParameters parameters
  ) throws Exception;

  List<String> doctor(DiscoveredProject project, AnalysisParameters parameters) throws Exception;
}
