package com.donatomartin.sonarcli.core.model;

import java.util.Map;

public record ReportStats(
  int totalFiles,
  Map<String, Integer> filesByKind,
  int issues,
  int parsingErrors,
  long durationMs
) {}
