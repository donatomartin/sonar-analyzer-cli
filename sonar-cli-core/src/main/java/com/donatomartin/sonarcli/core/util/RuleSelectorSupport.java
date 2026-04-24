package com.donatomartin.sonarcli.core.util;

import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.model.RuleFamily;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class RuleSelectorSupport {

  private static final Set<String> REMOTE_ONLY_PREFIXES = Set.of(
    "jssecurity",
    "tssecurity",
    "jsarchitecture",
    "tsarchitecture"
  );

  private RuleSelectorSupport() {}

  public static String primarySelector(RuleDefinition rule) {
    return primaryPrefix(rule) + ":" + rule.rawKey();
  }

  public static List<String> selectorAliases(RuleDefinition rule) {
    var aliases = new LinkedHashSet<String>();
    switch (rule.family()) {
      case JAVA -> aliases.add("java:" + rule.rawKey());
      case CSS -> aliases.add("css:" + rule.rawKey());
      case JS -> {
        if (supportsLanguage(rule, "js")) {
          aliases.add("javascript:" + rule.rawKey());
        }
        if (supportsLanguage(rule, "ts")) {
          aliases.add("typescript:" + rule.rawKey());
        }
        aliases.add("js:" + rule.rawKey());
      }
    }
    aliases.remove(primarySelector(rule));
    return List.copyOf(aliases);
  }

  public static List<String> repoSelectors(RuleDefinition rule) {
    var selectors = new LinkedHashSet<String>();
    selectors.add(primarySelector(rule));
    selectors.addAll(selectorAliases(rule));
    return List.copyOf(selectors);
  }

  public static boolean matchesRequestedFamily(RuleDefinition rule, String requestedFamily) {
    if (requestedFamily == null || requestedFamily.isBlank()) {
      return true;
    }
    var normalized = requestedFamily.trim().toLowerCase(Locale.ROOT);
    if (rule.family().name().equalsIgnoreCase(normalized) || rule.family().selectorPrefix().equals(normalized)) {
      return true;
    }
    return switch (normalized) {
      case "javascript" -> supportsLanguage(rule, "js");
      case "typescript", "ts" -> supportsLanguage(rule, "ts");
      default -> false;
    };
  }

  public static String selectorForIssue(RuleDefinition rule, String rawKey, String language, Path path) {
    var normalizedLanguage = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
    if ("css".equals(normalizedLanguage) || (path != null && hasExtension(path, ".css", ".scss", ".sass", ".less"))) {
      return "css:" + rawKey;
    }
    if ("ts".equals(normalizedLanguage) || (path != null && hasExtension(path, ".ts", ".tsx", ".mts", ".cts"))) {
      return "typescript:" + rawKey;
    }
    if ("java".equals(normalizedLanguage) || (path != null && hasExtension(path, ".java"))) {
      return "java:" + rawKey;
    }
    if (rule != null) {
      if (rule.family() == RuleFamily.CSS) {
        return "css:" + rawKey;
      }
      if (rule.family() == RuleFamily.JAVA) {
        return "java:" + rawKey;
      }
      if (supportsLanguage(rule, "ts") && !supportsLanguage(rule, "js")) {
        return "typescript:" + rawKey;
      }
    }
    return "javascript:" + rawKey;
  }

  public static boolean supportsLanguage(RuleDefinition rule, String language) {
    return rule.languages().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(language));
  }

  public static Optional<String> remoteOnlySelectionWarning(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    int separator = token.indexOf(':');
    if (separator < 1) {
      return Optional.empty();
    }
    var prefix = token.substring(0, separator).trim().toLowerCase(Locale.ROOT);
    if (!REMOTE_ONLY_PREFIXES.contains(prefix)) {
      return Optional.empty();
    }
    if (prefix.endsWith("security")) {
      return Optional.of(
        "Requested " + token + ", but Advanced Security injection rules are not bundled in standalone analysis. "
          + "The selector was recognized and skipped locally."
      );
    }
    return Optional.of(
      "Requested " + token + ", but architecture rules are not available in standalone local analysis. "
        + "The selector was recognized and skipped locally."
    );
  }

  private static String primaryPrefix(RuleDefinition rule) {
    return switch (rule.family()) {
      case JAVA -> "java";
      case CSS -> "css";
      case JS -> supportsLanguage(rule, "ts") && !supportsLanguage(rule, "js") ? "typescript" : "javascript";
    };
  }

  private static boolean hasExtension(Path path, String... extensions) {
    var normalized = path.getFileName().toString().toLowerCase(Locale.ROOT);
    for (String extension : extensions) {
      if (normalized.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }
}
