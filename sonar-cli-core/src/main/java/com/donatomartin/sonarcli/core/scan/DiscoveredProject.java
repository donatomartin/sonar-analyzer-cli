package com.donatomartin.sonarcli.core.scan;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DiscoveredProject(
  Path baseDir,
  List<ScannedFile> files,
  List<Path> tsconfigPaths
) {
  public List<ScannedFile> filesOfKind(FileKind kind) {
    return files.stream().filter(file -> file.kind() == kind).toList();
  }

  public List<ScannedFile> filesForAnalyzer(String analyzerId) {
    return files.stream().filter(file -> file.kind().analyzerId().equals(analyzerId)).toList();
  }

  public int totalFiles() {
    return files.size();
  }

  public Map<String, Integer> fileCountsByKind() {
    var counts = new EnumMap<FileKind, Integer>(FileKind.class);
    for (ScannedFile file : files) {
      counts.merge(file.kind(), 1, Integer::sum);
    }
    var result = new java.util.LinkedHashMap<String, Integer>();
    for (FileKind kind : FileKind.values()) {
      result.put(kind.statsKey(), counts.getOrDefault(kind, 0));
    }
    return Map.copyOf(result);
  }
}
