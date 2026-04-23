package com.donatomartin.sonarcli.core.model;

import java.util.List;

public record RuleConfigFile(
  List<String> profiles,
  List<String> enable,
  List<String> disable
) {
  public static RuleConfigFile empty() {
    return new RuleConfigFile(List.of(), List.of(), List.of());
  }
}
