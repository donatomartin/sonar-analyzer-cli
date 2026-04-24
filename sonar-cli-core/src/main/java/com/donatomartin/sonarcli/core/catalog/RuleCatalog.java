package com.donatomartin.sonarcli.core.catalog;

import com.donatomartin.sonarcli.core.model.RuleConfigFile;
import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.util.RuleConfigSupport;
import com.donatomartin.sonarcli.core.util.RuleSelectorSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RuleCatalog {

  private final List<RuleDefinition> rules;
  private final Map<String, RuleDefinition> bySelector;
  private final Map<String, List<RuleDefinition>> byRawKey;

  public RuleCatalog(List<RuleDefinition> rules) {
    this.rules = rules.stream()
      .map(rule -> new RuleDefinition(
        rule.analyzerId(),
        rule.family(),
        rule.rawKey(),
        RuleSelectorSupport.primarySelector(rule),
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
    this.bySelector = new LinkedHashMap<>();
    this.byRawKey = new LinkedHashMap<>();
    for (RuleDefinition rule : this.rules) {
      bySelector.put(normalize(rule.selector()), rule);
      for (String alias : RuleSelectorSupport.selectorAliases(rule)) {
        bySelector.put(normalize(alias), rule);
      }
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
    return exact == null ? List.of() : List.of(exact);
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
    if (rulesProfileOrFile == null || rulesProfileOrFile.isBlank()) {
      return resolveSelection((RuleConfigFile) null, enableTokens, disableTokens, analyzerIds);
    }
    var candidatePath = Path.of(rulesProfileOrFile);
    if (Files.exists(candidatePath)) {
      return resolveSelection(RuleConfigSupport.read(candidatePath), enableTokens, disableTokens, analyzerIds);
    }
    return resolveSelection(
      new RuleConfigFile(List.of(rulesProfileOrFile), List.of(), List.of()),
      enableTokens,
      disableTokens,
      analyzerIds
    );
  }

  public SelectedRuleSet resolveSelection(RuleConfigFile baseConfig, Set<String> analyzerIds) {
    return resolveSelection(baseConfig, List.of(), List.of(), analyzerIds);
  }

  public List<String> availableProfiles(Set<String> analyzerIds) {
    var profiles = new LinkedHashSet<String>();
    for (RuleDefinition rule : allRules(analyzerIds)) {
      profiles.addAll(rule.defaultQualityProfiles());
    }
    return List.copyOf(profiles);
  }

  private SelectedRuleSet resolveSelection(
    RuleConfigFile baseConfig,
    List<String> enableTokens,
    List<String> disableTokens,
    Set<String> analyzerIds
  ) {
    var enabled = new LinkedHashMap<String, RuleDefinition>();
    var selectionWarnings = new ArrayList<String>();
    var availableRules = allRules(analyzerIds);
    if (baseConfig == null) {
      selectDefaultRules(enabled, availableRules);
    } else {
      applyRuleConfig(enabled, availableRules, baseConfig, analyzerIds, selectionWarnings);
    }

    applyTokens(enabled, enableTokens, analyzerIds, true, selectionWarnings);
    applyTokens(enabled, disableTokens, analyzerIds, false, selectionWarnings);

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

    return new SelectedRuleSet(selectedRules, Map.copyOf(rulesByAnalyzer), List.copyOf(selectionWarnings));
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

  private void applyRuleConfig(
    Map<String, RuleDefinition> enabled,
    List<RuleDefinition> availableRules,
    RuleConfigFile config,
    Set<String> analyzerIds,
    List<String> selectionWarnings
  ) {
    if (config.profiles() == null || config.profiles().isEmpty()) {
      enabled.clear();
    } else {
      for (String profile : config.profiles()) {
        selectProfile(enabled, availableRules, profile);
      }
    }
    applyTokens(enabled, config.enable(), analyzerIds, true, selectionWarnings);
    applyTokens(enabled, config.disable(), analyzerIds, false, selectionWarnings);
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
    boolean add,
    List<String> selectionWarnings
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
        var remoteOnlyWarning = RuleSelectorSupport.remoteOnlySelectionWarning(token);
        if (remoteOnlyWarning.isPresent()) {
          selectionWarnings.add(remoteOnlyWarning.get());
          continue;
        }
        throw new IllegalArgumentException("Unknown rule selector: " + token + RuleSelectorSupport.prefixedSelectorHint(token));
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
    var matches = byRawKey.getOrDefault(normalize(rawKey), List.of()).stream()
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
}
