package com.donatomartin.sonarcli.core.model;

import java.util.List;
import java.util.Map;

public record AnalysisReport(
  String engine,
  String engineVersion,
  String baseDir,
  List<String> analyzers,
  Map<String, String> analyzerVersions,
  List<String> activeRules,
  List<String> warnings,
  List<IssueRecord> issues,
  ReportStats stats,
  Map<String, Integer> thresholds,
  Map<String, Integer> thresholdsBySeverity
) {}
