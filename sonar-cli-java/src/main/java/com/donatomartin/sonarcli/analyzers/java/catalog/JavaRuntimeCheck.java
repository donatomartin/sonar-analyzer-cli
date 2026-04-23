package com.donatomartin.sonarcli.analyzers.java.catalog;

import com.donatomartin.sonarcli.core.model.SourceType;
import org.sonar.plugins.java.api.JavaCheck;

public record JavaRuntimeCheck(
  Class<? extends JavaCheck> checkClass,
  SourceType sourceType
) {}
