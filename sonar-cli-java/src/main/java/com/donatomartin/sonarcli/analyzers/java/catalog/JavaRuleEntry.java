package com.donatomartin.sonarcli.analyzers.java.catalog;

import com.donatomartin.sonarcli.core.model.RuleDefinition;
import java.util.List;

public record JavaRuleEntry(
  RuleDefinition definition,
  List<JavaRuntimeCheck> runtimeChecks
) {}
