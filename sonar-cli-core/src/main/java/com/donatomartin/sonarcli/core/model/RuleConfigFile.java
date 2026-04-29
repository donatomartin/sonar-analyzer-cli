package com.donatomartin.sonarcli.core.model;

import java.util.List;
import java.util.Map;

public record RuleConfigFile(
  List<String> profiles,
  List<String> enable,
  List<String> disable,
  Map<String, Integer> thresholds,
  Map<String, Integer> thresholdsBySeverity
) {
  public static RuleConfigFile empty() {
    return new RuleConfigFile(List.of(), List.of(), List.of(), Map.of(), Map.of());
  }

  public boolean hasThresholds() {
    return (thresholds != null && !thresholds.isEmpty())
      || (thresholdsBySeverity != null && !thresholdsBySeverity.isEmpty());
  }
}
