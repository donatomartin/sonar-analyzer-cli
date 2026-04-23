package com.donatomartin.sonarcli.core.catalog;

import com.donatomartin.sonarcli.core.model.RuleDefinition;
import java.util.List;
import java.util.Map;

public record SelectedRuleSet(
  List<RuleDefinition> selectedRules,
  Map<String, List<RuleDefinition>> rulesByAnalyzer
) {
  public List<RuleDefinition> forAnalyzer(String analyzerId) {
    return rulesByAnalyzer.getOrDefault(analyzerId, List.of());
  }
}
