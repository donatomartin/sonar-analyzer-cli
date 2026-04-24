package com.donatomartin.sonarcli.app.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ProjectRootResolver {

  private static final List<String> STRONG_MARKERS = List.of(
    "pom.xml",
    "mvnw",
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "gradlew",
    "package.json",
    "tsconfig.json",
    "tsconfig.base.json"
  );

  private ProjectRootResolver() {}

  static Path inferBaseDir(Path file) {
    Path parent = file.toAbsolutePath().normalize().getParent();
    if (parent == null) {
      return file.toAbsolutePath().normalize();
    }

    for (Path current = parent; current != null; current = current.getParent()) {
      if (containsAny(current, STRONG_MARKERS)) {
        return current;
      }
    }

    for (Path current = parent; current != null; current = current.getParent()) {
      if (Files.exists(current.resolve(".git"))) {
        return current;
      }
    }

    return parent;
  }

  private static boolean containsAny(Path directory, List<String> names) {
    for (String name : names) {
      if (Files.exists(directory.resolve(name))) {
        return true;
      }
    }
    return false;
  }
}
