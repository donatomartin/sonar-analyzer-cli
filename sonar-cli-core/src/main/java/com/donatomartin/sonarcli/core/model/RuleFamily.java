package com.donatomartin.sonarcli.core.model;

public enum RuleFamily {
  JS("js"),
  CSS("css"),
  JAVA("java");

  private final String selectorPrefix;

  RuleFamily(String selectorPrefix) {
    this.selectorPrefix = selectorPrefix;
  }

  public String selectorPrefix() {
    return selectorPrefix;
  }
}
