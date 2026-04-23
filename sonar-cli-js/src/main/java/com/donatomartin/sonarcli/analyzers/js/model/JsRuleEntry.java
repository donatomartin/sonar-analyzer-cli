package com.donatomartin.sonarcli.analyzers.js.model;

import com.donatomartin.sonarcli.core.model.RuleDefinition;
import java.util.List;

public record JsRuleEntry(
  RuleDefinition definition,
  List<JsRuntimeRule> jsRuntimeRules,
  CssRuntimeRule cssRuntimeRule
) {}
