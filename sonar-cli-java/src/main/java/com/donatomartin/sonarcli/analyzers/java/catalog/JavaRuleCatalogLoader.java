package com.donatomartin.sonarcli.analyzers.java.catalog;

import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.model.RuleFamily;
import com.donatomartin.sonarcli.core.model.SourceType;
import com.donatomartin.sonarcli.core.util.JsonSupport;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sonar.check.Rule;
import org.sonar.java.GeneratedCheckList;
import org.sonar.plugins.java.api.JavaCheck;

public final class JavaRuleCatalogLoader {

  public List<JavaRuleEntry> load() {
    var merged = new LinkedHashMap<String, MutableJavaRule>();
    mergeChecks(GeneratedCheckList.getJavaChecks(), SourceType.MAIN, merged);
    mergeChecks(GeneratedCheckList.getJavaTestChecks(), SourceType.TEST, merged);
    return merged.values().stream()
      .sorted(Comparator.comparing(MutableJavaRule::rawKey))
      .map(MutableJavaRule::toEntry)
      .toList();
  }

  private static void mergeChecks(
    List<Class<? extends JavaCheck>> checks,
    SourceType sourceType,
    Map<String, MutableJavaRule> merged
  ) {
    for (Class<? extends JavaCheck> checkClass : checks) {
      Rule rule = checkClass.getAnnotation(Rule.class);
      if (rule == null) {
        continue;
      }
      var rawKey = rule.key();
      var metadata = loadMetadata("/org/sonar/l10n/java/rules/java/" + rawKey + ".json");
      if (metadata == null) {
        continue;
      }
      var description = loadTextResource("/org/sonar/l10n/java/rules/java/" + rawKey + ".html");
      merged.computeIfAbsent(rawKey, ignored -> new MutableJavaRule(rawKey, metadata, description))
        .addRuntimeCheck(new JavaRuntimeCheck(checkClass, sourceType));
    }
  }

  private static RuleMetadata loadMetadata(String resourcePath) {
    try (InputStream stream = JavaRuleCatalogLoader.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        return null;
      }
      return JsonSupport.GSON.fromJson(new String(stream.readAllBytes()), RuleMetadata.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load resource: " + resourcePath, e);
    }
  }

  private static String loadTextResource(String resourcePath) {
    try (InputStream stream = JavaRuleCatalogLoader.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        return "";
      }
      return new String(stream.readAllBytes());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load resource: " + resourcePath, e);
    }
  }

  private static List<String> safeList(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  private static List<String> defaultProfiles(RuleMetadata metadata) {
    var profiles = safeList(metadata.defaultQualityProfiles());
    return profiles.isEmpty() ? List.of("Sonar way") : profiles;
  }

  private record RuleMetadata(
    String title,
    String type,
    String defaultSeverity,
    List<String> tags,
    String ruleSpecification,
    List<String> compatibleLanguages,
    List<String> defaultQualityProfiles
  ) {}

  private static final class MutableJavaRule {
    private final String rawKey;
    private final RuleMetadata metadata;
    private final String descriptionHtml;
    private final List<JavaRuntimeCheck> runtimeChecks = new ArrayList<>();

    private MutableJavaRule(String rawKey, RuleMetadata metadata, String descriptionHtml) {
      this.rawKey = rawKey;
      this.metadata = metadata;
      this.descriptionHtml = descriptionHtml;
    }

    private String rawKey() {
      return rawKey;
    }

    private void addRuntimeCheck(JavaRuntimeCheck runtimeCheck) {
      var exists = runtimeChecks.stream().anyMatch(existing ->
        existing.checkClass().equals(runtimeCheck.checkClass()) && existing.sourceType() == runtimeCheck.sourceType()
      );
      if (!exists) {
        runtimeChecks.add(runtimeCheck);
      }
    }

    private JavaRuleEntry toEntry() {
      return new JavaRuleEntry(
        new RuleDefinition(
          "java",
          RuleFamily.JAVA,
          rawKey,
          rawKey,
          metadata.title(),
          descriptionHtml,
          metadata.type(),
          metadata.defaultSeverity(),
          safeList(metadata.tags()),
          metadata.ruleSpecification(),
          List.of("java"),
          defaultProfiles(metadata)
        ),
        List.copyOf(runtimeChecks)
      );
    }
  }
}
