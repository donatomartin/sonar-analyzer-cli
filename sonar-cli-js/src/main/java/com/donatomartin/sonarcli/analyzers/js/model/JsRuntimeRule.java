package com.donatomartin.sonarcli.analyzers.js.model;

import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.plugins.javascript.api.AnalysisMode;

public record JsRuntimeRule(
  String rawKey,
  List<Object> configurations,
  List<InputFile.Type> targets,
  List<AnalysisMode> analysisModes,
  List<String> blacklistedExtensions,
  String language
) {}
