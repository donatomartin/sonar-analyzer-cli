package com.donatomartin.sonarcli.core.runtime;

import com.donatomartin.sonarcli.core.model.IssueRecord;
import java.util.List;
import java.util.Map;

public record AnalyzerResult(
  String analyzerId,
  String analyzerVersion,
  List<String> warnings,
  List<IssueRecord> issues,
  int parsingErrors,
  Map<String, Integer> fileCounts
) {}
