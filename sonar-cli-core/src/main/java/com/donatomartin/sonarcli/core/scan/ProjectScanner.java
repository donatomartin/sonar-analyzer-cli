package com.donatomartin.sonarcli.core.scan;

import com.donatomartin.sonarcli.core.model.SourceType;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProjectScanner {

  private static final Set<String> DEFAULT_SKIPPED_DIRECTORIES = Set.of(
    ".git",
    ".gradle",
    ".idea",
    ".scannerwork",
    "build",
    "coverage",
    "dist",
    "node_modules",
    "out",
    "target"
  );

  private static final Set<String> JS_TS_EXTENSIONS = Set.of(
    ".js",
    ".jsx",
    ".mjs",
    ".cjs",
    ".ts",
    ".tsx",
    ".mts",
    ".cts"
  );
  private static final Set<String> HTML_EXTENSIONS = Set.of(".html", ".vue");
  private static final Set<String> YAML_EXTENSIONS = Set.of(".yaml", ".yml");
  private static final Set<String> CSS_EXTENSIONS = Set.of(".css", ".scss", ".sass", ".less");
  private static final Set<String> JAVA_EXTENSIONS = Set.of(".java");
  private static final Pattern TEST_NAME_PATTERN = Pattern.compile(".*(\\.test\\.|\\.spec\\.|/__tests__/|/src/test/).*");

  public DiscoveredProject scan(
    Path baseDir,
    List<String> includes,
    List<String> excludes,
    List<String> tests,
    List<Path> explicitTsconfigs
  ) throws IOException {
    var normalizedBaseDir = baseDir.toAbsolutePath().normalize();
    var includeMatchers = toMatchers(includes);
    var excludeMatchers = toMatchers(excludes);
    var testMatchers = toMatchers(tests);
    var files = new ArrayList<ScannedFile>();
    var tsconfigs = new HashSet<Path>();
    tsconfigs.addAll(explicitTsconfigs.stream().map(path -> path.toAbsolutePath().normalize()).toList());

    FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (!normalizedBaseDir.equals(dir) && DEFAULT_SKIPPED_DIRECTORIES.contains(dir.getFileName().toString())) {
          return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        var normalizedFile = file.toAbsolutePath().normalize();
        var relativePath = normalizedBaseDir.relativize(normalizedFile).toString().replace('\\', '/');
        if (isTsconfig(relativePath)) {
          tsconfigs.add(normalizedFile);
          return FileVisitResult.CONTINUE;
        }
        var kind = classify(normalizedFile);
        if (kind == null) {
          return FileVisitResult.CONTINUE;
        }
        if (!includeMatchers.isEmpty() && !matchesAny(relativePath, includeMatchers)) {
          return FileVisitResult.CONTINUE;
        }
        if (matchesAny(relativePath, excludeMatchers)) {
          return FileVisitResult.CONTINUE;
        }
        files.add(new ScannedFile(
          normalizedFile,
          relativePath,
          inferType(relativePath, testMatchers),
          kind
        ));
        return FileVisitResult.CONTINUE;
      }
    };

    Files.walkFileTree(normalizedBaseDir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, visitor);
    return new DiscoveredProject(normalizedBaseDir, List.copyOf(files), tsconfigs.stream().sorted().toList());
  }

  private static List<PathMatcher> toMatchers(List<String> patterns) {
    return patterns.stream()
      .filter(pattern -> pattern != null && !pattern.isBlank())
      .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
      .toList();
  }

  private static boolean matchesAny(String path, List<PathMatcher> matchers) {
    if (matchers.isEmpty()) {
      return false;
    }
    var candidate = Path.of(path);
    return matchers.stream().anyMatch(matcher -> matcher.matches(candidate));
  }

  private static boolean isTsconfig(String relativePath) {
    var filename = Path.of(relativePath).getFileName().toString().toLowerCase(Locale.ROOT);
    return filename.startsWith("tsconfig") && filename.endsWith(".json");
  }

  private static SourceType inferType(String relativePath, List<PathMatcher> testMatchers) {
    if (matchesAny(relativePath, testMatchers) || TEST_NAME_PATTERN.matcher('/' + relativePath).matches()) {
      return SourceType.TEST;
    }
    return SourceType.MAIN;
  }

  private static FileKind classify(Path file) {
    var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (endsWithAny(name, JS_TS_EXTENSIONS)) {
      return FileKind.JS_TS;
    }
    if (endsWithAny(name, HTML_EXTENSIONS)) {
      return FileKind.HTML;
    }
    if (endsWithAny(name, YAML_EXTENSIONS)) {
      return FileKind.YAML;
    }
    if (endsWithAny(name, CSS_EXTENSIONS)) {
      return FileKind.CSS;
    }
    if (endsWithAny(name, JAVA_EXTENSIONS)) {
      return FileKind.JAVA;
    }
    return null;
  }

  private static boolean endsWithAny(String name, Set<String> extensions) {
    for (String extension : extensions) {
      if (name.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }
}
