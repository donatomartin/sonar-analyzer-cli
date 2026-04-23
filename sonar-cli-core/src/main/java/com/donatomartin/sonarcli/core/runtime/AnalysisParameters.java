package com.donatomartin.sonarcli.core.runtime;

import java.nio.file.Path;
import java.util.List;

public record AnalysisParameters(
  Path baseDir,
  List<String> analyzers,
  List<Path> explicitTsconfigs,
  List<String> includes,
  List<String> excludes,
  List<String> tests,
  long maxFileSizeBytes,
  int maxFilesForTypeChecking,
  List<String> globals,
  List<String> environments,
  String nodeExecutable,
  boolean skipNodeProvisioning,
  boolean debugMemory,
  List<Path> javaBinaries,
  List<Path> javaLibraries,
  List<Path> javaTestBinaries,
  List<Path> javaTestLibraries,
  String javaSourceVersion,
  boolean javaEnablePreview,
  Path javaJdkHome
) {}
