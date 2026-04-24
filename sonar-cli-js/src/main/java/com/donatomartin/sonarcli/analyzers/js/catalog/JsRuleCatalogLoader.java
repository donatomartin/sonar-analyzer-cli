package com.donatomartin.sonarcli.analyzers.js.catalog;

import com.donatomartin.sonarcli.analyzers.js.model.CssRuntimeRule;
import com.donatomartin.sonarcli.analyzers.js.model.JsRuleEntry;
import com.donatomartin.sonarcli.analyzers.js.model.JsRuntimeRule;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.model.RuleFamily;
import com.donatomartin.sonarcli.core.util.JsonSupport;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.check.Rule;
import org.sonar.css.rules.CssRule;
import org.sonar.plugins.javascript.api.EslintHook;
import org.sonar.plugins.javascript.api.JavaScriptRule;
import org.sonar.plugins.javascript.api.TypeScriptRule;

public final class JsRuleCatalogLoader {

  public List<JsRuleEntry> load() {
    var jsRules = new LinkedHashMap<String, MutableJsRule>();
    var cssRules = new LinkedHashMap<String, MutableCssRule>();

    try (
      ScanResult scanResult = new ClassGraph()
        .acceptPackages("org.sonar.javascript.checks", "org.sonar.css.rules")
        .enableClassInfo()
        .enableAnnotationInfo()
        .scan()
    ) {
      loadJavaScriptRules(scanResult, jsRules);
      loadCssRules(scanResult, cssRules);
    }

    var rawRules = new ArrayList<JsRuleEntry>();
    jsRules.values().stream().sorted(Comparator.comparing(MutableJsRule::rawKey)).forEach(rule -> rawRules.add(rule.toEntry()));
    cssRules.values().stream().sorted(Comparator.comparing(MutableCssRule::rawKey)).forEach(rule -> rawRules.add(rule.toEntry()));

    return rawRules.stream().map(entry -> {
      var definition = entry.definition();
      return new JsRuleEntry(
        new RuleDefinition(
          "js",
          definition.family(),
          definition.rawKey(),
          definition.rawKey(),
          definition.title(),
          definition.descriptionHtml(),
          definition.type(),
          definition.defaultSeverity(),
          definition.tags(),
          definition.ruleSpecification(),
          definition.languages(),
          definition.defaultQualityProfiles()
        ),
        entry.jsRuntimeRules(),
        entry.cssRuntimeRule()
      );
    }).toList();
  }

  private void loadJavaScriptRules(ScanResult scanResult, Map<String, MutableJsRule> jsRules) {
    for (ClassInfo classInfo : scanResult.getClassesImplementing(EslintHook.class)) {
      if (classInfo.isAbstract() || !classInfo.hasAnnotation(Rule.class)) {
        continue;
      }
      Class<?> rawClass = classInfo.loadClass();
      Rule ruleAnnotation = rawClass.getAnnotation(Rule.class);
      var rawKey = ruleAnnotation.key();
      var metadata = loadMetadata("/org/sonar/l10n/javascript/rules/javascript/" + rawKey + ".json");
      if (metadata == null) {
        continue;
      }
      var description = loadTextResource("/org/sonar/l10n/javascript/rules/javascript/" + rawKey + ".html");
      var hook = instantiate(rawClass, EslintHook.class);
      var languages = languagesFor(rawClass, metadata.compatibleLanguages);
      var runtimeRules = new ArrayList<JsRuntimeRule>();
      for (String language : languages) {
        runtimeRules.add(
          new JsRuntimeRule(
            hook.eslintKey(),
            List.copyOf(hook.configurations()),
            List.copyOf(hook.targets()),
            List.copyOf(hook.analysisModes()),
            List.copyOf(hook.blacklistedExtensions()),
            language
          )
        );
      }
      jsRules.computeIfAbsent(rawKey, ignored -> new MutableJsRule(rawKey, metadata, description)).merge(languages, runtimeRules);
    }
  }

  private void loadCssRules(ScanResult scanResult, Map<String, MutableCssRule> cssRules) {
    for (ClassInfo classInfo : scanResult.getClassesImplementing(CssRule.class)) {
      if (classInfo.isAbstract() || !classInfo.hasAnnotation(Rule.class)) {
        continue;
      }
      Class<?> rawClass = classInfo.loadClass();
      Rule ruleAnnotation = rawClass.getAnnotation(Rule.class);
      var rawKey = ruleAnnotation.key();
      var metadata = loadMetadata("/org/sonar/l10n/css/rules/css/" + rawKey + ".json");
      if (metadata == null) {
        continue;
      }
      var description = loadTextResource("/org/sonar/l10n/css/rules/css/" + rawKey + ".html");
      var cssRule = instantiate(rawClass, CssRule.class);
      cssRules.put(
        rawKey,
        new MutableCssRule(
          rawKey,
          metadata,
          description,
          new CssRuntimeRule(rawKey, cssRule.stylelintKey(), List.copyOf(cssRule.stylelintOptions()))
        )
      );
    }
  }

  private static List<String> languagesFor(Class<?> rawClass, List<String> compatibleLanguages) {
    var languages = new LinkedHashSet<String>();
    if (rawClass.isAnnotationPresent(JavaScriptRule.class)) {
      languages.add("js");
    }
    if (rawClass.isAnnotationPresent(TypeScriptRule.class)) {
      languages.add("ts");
    }
    if (languages.isEmpty() && compatibleLanguages != null) {
      compatibleLanguages.stream().map(language -> language.toLowerCase(Locale.ROOT)).forEach(languages::add);
    }
    return List.copyOf(languages);
  }

  private static <T> T instantiate(Class<?> rawClass, Class<T> expectedType) {
    try {
      return expectedType.cast(rawClass.getDeclaredConstructor().newInstance());
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new IllegalStateException("Failed to instantiate " + rawClass.getName(), e);
    }
  }

  private static RuleMetadata loadMetadata(String resourcePath) {
    try (InputStream stream = JsRuleCatalogLoader.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        return null;
      }
      return JsonSupport.GSON.fromJson(new String(stream.readAllBytes()), RuleMetadata.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load resource: " + resourcePath, e);
    }
  }

  private static String loadTextResource(String resourcePath) {
    try (InputStream stream = JsRuleCatalogLoader.class.getResourceAsStream(resourcePath)) {
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

  private record RuleMetadata(
    String title,
    String type,
    String defaultSeverity,
    List<String> tags,
    String ruleSpecification,
    String sqKey,
    List<String> compatibleLanguages,
    List<String> defaultQualityProfiles
  ) {}

  private static final class MutableJsRule {
    private final String rawKey;
    private final RuleMetadata metadata;
    private final String descriptionHtml;
    private final Set<String> languages = new LinkedHashSet<>();
    private final List<JsRuntimeRule> runtimeRules = new ArrayList<>();

    private MutableJsRule(String rawKey, RuleMetadata metadata, String descriptionHtml) {
      this.rawKey = rawKey;
      this.metadata = metadata;
      this.descriptionHtml = descriptionHtml;
    }

    private String rawKey() {
      return rawKey;
    }

    private void merge(List<String> languages, List<JsRuntimeRule> runtimeRules) {
      this.languages.addAll(languages);
      for (JsRuntimeRule runtimeRule : runtimeRules) {
        var exists = this.runtimeRules.stream().anyMatch(existing ->
          existing.rawKey().equals(runtimeRule.rawKey()) && existing.language().equals(runtimeRule.language())
        );
        if (!exists) {
          this.runtimeRules.add(runtimeRule);
        }
      }
    }

    private JsRuleEntry toEntry() {
      return new JsRuleEntry(
        new RuleDefinition(
          "js",
          RuleFamily.JS,
          rawKey,
          rawKey,
          metadata.title(),
          descriptionHtml,
          metadata.type(),
          metadata.defaultSeverity(),
          safeList(metadata.tags()),
          metadata.ruleSpecification(),
          List.copyOf(languages),
          safeList(metadata.defaultQualityProfiles())
        ),
        List.copyOf(runtimeRules),
        null
      );
    }
  }

  private static final class MutableCssRule {
    private final String rawKey;
    private final RuleMetadata metadata;
    private final String descriptionHtml;
    private final CssRuntimeRule runtimeRule;

    private MutableCssRule(String rawKey, RuleMetadata metadata, String descriptionHtml, CssRuntimeRule runtimeRule) {
      this.rawKey = rawKey;
      this.metadata = metadata;
      this.descriptionHtml = descriptionHtml;
      this.runtimeRule = runtimeRule;
    }

    private String rawKey() {
      return rawKey;
    }

    private JsRuleEntry toEntry() {
      return new JsRuleEntry(
        new RuleDefinition(
          "js",
          RuleFamily.CSS,
          rawKey,
          rawKey,
          metadata.title(),
          descriptionHtml,
          metadata.type(),
          metadata.defaultSeverity(),
          safeList(metadata.tags()),
          metadata.ruleSpecification(),
          List.of("css"),
          safeList(metadata.defaultQualityProfiles())
        ),
        List.of(),
        runtimeRule
      );
    }
  }
}
