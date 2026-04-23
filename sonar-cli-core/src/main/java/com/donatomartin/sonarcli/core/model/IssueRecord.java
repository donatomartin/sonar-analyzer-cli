package com.donatomartin.sonarcli.core.model;

import java.util.List;

public record IssueRecord(
  String analyzerId,
  RuleFamily family,
  String ruleKey,
  String selector,
  String path,
  int startLine,
  int startLineOffset,
  int endLine,
  int endLineOffset,
  String message,
  String severity,
  String type,
  List<IssueLocationRecord> secondaryLocations
) {}
