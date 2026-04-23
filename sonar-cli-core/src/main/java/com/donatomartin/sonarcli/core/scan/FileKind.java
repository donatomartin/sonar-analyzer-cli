package com.donatomartin.sonarcli.core.scan;

public enum FileKind {
  JS_TS("js", "js-ts"),
  HTML("js", "html"),
  YAML("js", "yaml"),
  CSS("js", "css"),
  JAVA("java", "java");

  private final String analyzerId;
  private final String statsKey;

  FileKind(String analyzerId, String statsKey) {
    this.analyzerId = analyzerId;
    this.statsKey = statsKey;
  }

  public String analyzerId() {
    return analyzerId;
  }

  public String statsKey() {
    return statsKey;
  }
}
