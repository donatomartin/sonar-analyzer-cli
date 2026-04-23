package com.donatomartin.sonarcli.core.catalog;

import com.donatomartin.sonarcli.core.model.RuleConfigFile;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.model.RuleFamily;
import com.donatomartin.sonarcli.core.util.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

public final class RuleCatalog {

  private final List<RuleDefinition> rules;
  private final Map<String, RuleDefinition> bySelector;
  private final Map<String, List<RuleDefinition>> byRawKey;

  public RuleCatalog(List<RuleDefinition> rules) {
    var collisions = rules.stream().collect(
      LinkedHashMap<String, Integer>::new,
      (map, rule) -> map.merge(rule.rawKey(), 1, Integer::sum),
      Map::putAll
    );
    this.rules = rules.stream()
      .map(rule -> new RuleDefinition(
        rule.analyzerId(),
        rule.family(),
        rule.rawKey(),
        selectorFor(rule.family(), rule.rawKey(), collisions.getOrDefault(rule.rawKey(), 0) > 1),
        rule.title(),
        rule.descriptionHtml(),
        rule.type(),
        rule.defaultSeverity(),
        rule.tags(),
        rule.ruleSpecification(),
        rule.languages(),
        rule.defaultQualityProfiles()
      ))
      .sorted(Comparator.comparing(RuleDefinition::selector))
      .toList();
    this.bySelector = this.rules.stream().collect(
      LinkedHashMap::new,
      (map, rule) -> map.put(normalize(rule.selector()), rule),
      Map::putAll
    );
    this.byRawKey = new LinkedHashMap<>();
    for (RuleDefinition rule : this.rules) {
      byRawKey.computeIfAbsent(normalize(rule.rawKey()), ignored -> new ArrayList<>()).add(rule);
    }
  }

  public List<RuleDefinition> allRules() {
    return rules;
  }

  public List<RuleDefinition> allRules(Set<String> analyzerIds) {
    if (analyzerIds == null || analyzerIds.isEmpty()) {
      return rules;
    }
    return rules.stream().filter(rule -> analyzerIds.contains(rule.analyzerId())).toList();
  }

  public List<RuleDefinition> find(String selectorOrKey) {
    var exact = bySelector.get(normalize(selectorOrKey));
    if (exact != null) {
      return List.of(exact);
    }
    return byRawKey.getOrDefault(normalize(selectorOrKey), List.of());
  }

  public List<RuleDefinition> find(String selectorOrKey, Set<String> analyzerIds) {
    return find(selectorOrKey).stream()
      .filter(rule -> analyzerIds == null || analyzerIds.isEmpty() || analyzerIds.contains(rule.analyzerId()))
      .toList();
  }

  public SelectedRuleSet resolveSelection(
    String rulesProfileOrFile,
    List<String> enableTokens,
    List<String> disableTokens,
    Set<String> analyzerIds
  ) throws IOException {
    var enabled = new LinkedHashMap<String, RuleDefinition>();
    var availableRules = allRules(analyzerIds);
    if (rulesProfileOrFile == null || rulesProfileOrFile.isBlank()) {
      selectDefaultRules(enabled, availableRules);
    } else {
      var candidatePath = Path.of(rulesProfileOrFile);
      if (Files.exists(candidatePath)) {
        applyRuleFile(enabled, availableRules, candidatePath, analyzerIds);
      } else {
        selectProfile(enabled, availableRules, rulesProfileOrFile);
      }
    }

    applyTokens(enabled, enableTokens, analyzerIds, true);
    applyTokens(enabled, disableTokens, analyzerIds, false);

    var selectedRules = enabled.values().stream()
      .sorted(Comparator.comparing(RuleDefinition::selector))
      .toList();
    var rulesByAnalyzer = selectedRules.stream().collect(
      LinkedHashMap<String, List<RuleDefinition>>::new,
      (map, rule) -> map.compute(rule.analyzerId(), (key, existing) -> {
        var mutable = existing == null ? new ArrayList<RuleDefinition>() : new ArrayList<>(existing);
        mutable.add(rule);
        return List.copyOf(mutable);
      }),
      Map::putAll
    );

    return new SelectedRuleSet(selectedRules, Map.copyOf(rulesByAnalyzer));
  }

  private static void selectDefaultRules(Map<String, RuleDefinition> enabled, List<RuleDefinition> availableRules) {
    var byAnalyzer = availableRules.stream().collect(
      LinkedHashMap<String, List<RuleDefinition>>::new,
      (map, rule) -> map.compute(rule.analyzerId(), (key, existing) -> {
        var mutable = existing == null ? new ArrayList<RuleDefinition>() : new ArrayList<>(existing);
        mutable.add(rule);
        return mutable;
      }),
      Map::putAll
    );
    for (List<RuleDefinition> analyzerRules : byAnalyzer.values()) {
      var profileMatches = analyzerRules.stream().filter(rule -> rule.inProfile("Sonar way")).toList();
      var defaults = profileMatches.isEmpty() ? analyzerRules : profileMatches;
      for (RuleDefinition rule : defaults) {
        enabled.put(internalKey(rule), rule);
      }
    }
  }

  private void applyRuleFile(
    Map<String, RuleDefinition> enabled,
    List<RuleDefinition> availableRules,
    Path candidatePath,
    Set<String> analyzerIds
  ) throws IOException {
    var config = parseRuleFile(candidatePath);
    if (config.profiles() == null || config.profiles().isEmpty()) {
      enabled.clear();
    } else {
      for (String profile : config.profiles()) {
        selectProfile(enabled, availableRules, profile);
      }
    }
    applyTokens(enabled, config.enable(), analyzerIds, true);
    applyTokens(enabled, config.disable(), analyzerIds, false);
  }

  private RuleConfigFile parseRuleFile(Path file) throws IOException {
    var content = Files.readString(file);
    var filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
      Object loaded = new Yaml().load(content);
      if (!(loaded instanceof Map<?, ?> map)) {
        return RuleConfigFile.empty();
      }
      return new RuleConfigFile(
        coerceList(map.get("profiles")),
        coerceList(map.get("enable")),
        coerceList(map.get("disable"))
      );
    }
    var parsed = JsonSupport.GSON.fromJson(content, RuleConfigFile.class);
    return parsed == null ? RuleConfigFile.empty() : parsed;
  }

  private static List<String> coerceList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
    if (value instanceof String string) {
      return List.of(string);
    }
    return List.of(String.valueOf(value));
  }

  private static void selectProfile(
    Map<String, RuleDefinition> enabled,
    List<RuleDefinition> availableRules,
    String profileName
  ) {
    var matches = availableRules.stream().filter(rule -> rule.inProfile(profileName)).toList();
    if (matches.isEmpty()) {
      throw new IllegalArgumentException("Unknown profile: " + profileName);
    }
    for (RuleDefinition match : matches) {
      enabled.put(internalKey(match), match);
    }
  }

  private void applyTokens(
    Map<String, RuleDefinition> enabled,
    List<String> tokens,
    Set<String> analyzerIds,
    boolean add
  ) {
    if (tokens == null) {
      return;
    }
    for (String token : tokens) {
      if (token == null || token.isBlank()) {
        continue;
      }
      var matches = find(token, analyzerIds);
      if (matches.isEmpty()) {
        throw new IllegalArgumentException("Unknown rule selector: " + token);
      }
      for (RuleDefinition match : matches) {
        if (add) {
          enabled.put(internalKey(match), match);
        } else {
          enabled.remove(internalKey(match));
        }
      }
    }
  }

  public RuleDefinition findForIssue(String rawKey, String analyzerId) {
    var matches = find(rawKey).stream()
      .filter(rule -> analyzerId == null || analyzerId.equals(rule.analyzerId()))
      .toList();
    return matches.isEmpty() ? null : matches.get(0);
  }

  private static String internalKey(RuleDefinition rule) {
    return rule.analyzerId() + ":" + rule.family() + ":" + rule.rawKey();
  }

  private static String normalize(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  public static String selectorFor(RuleFamily family, String rawKey, boolean ambiguous) {
    if (!ambiguous) {
      return rawKey;
    }
    return family.selectorPrefix() + ":" + rawKey;
  }
}
