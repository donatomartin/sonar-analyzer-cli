package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.core.util.RuleSelectorSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class CliCompletionCandidates {

  private CliCompletionCandidates() {}

  public static final class AnalyzerIds extends ArrayList<String> {
    public AnalyzerIds() {
      super(List.of("js", "java"));
    }
  }

  public static final class RuleFamilies extends ArrayList<String> {
    public RuleFamilies() {
      super(List.of("javascript", "typescript", "css", "java"));
    }
  }

  public static final class Shells extends ArrayList<String> {
    public Shells() {
      super(List.of("bash", "zsh"));
    }
  }

  public static final class RuleSelectors extends ArrayList<String> {
    public RuleSelectors() {
      super(loadRuleSelectors());
    }
  }

  public static final class ManagedProfileNames extends ArrayList<String> {
    public ManagedProfileNames() {
      super(loadManagedProfileNames());
    }
  }

  public static final class ManagedAndPackagedProfileNames extends ArrayList<String> {
    public ManagedAndPackagedProfileNames() {
      super(loadManagedAndPackagedProfileNames());
    }
  }

  private static List<String> loadRuleSelectors() {
    var selectors = new LinkedHashSet<String>();
    SonarAnalyzerCli.loadCatalog().allRules().forEach(rule -> RuleSelectorSupport.repoSelectors(rule).stream()
      .filter(selector -> !selector.startsWith("js:"))
      .forEach(selectors::add));
    return List.copyOf(selectors);
  }

  private static List<String> loadManagedProfileNames() {
    try {
      return ManagedProfileStore.defaultStore().listProfileNames();
    } catch (IOException ignored) {
      return List.of();
    }
  }

  private static List<String> loadManagedAndPackagedProfileNames() {
    var values = new LinkedHashSet<String>();
    values.addAll(loadManagedProfileNames());
    values.addAll(SonarAnalyzerCli.loadCatalog().availableProfiles(SonarAnalyzerCli.allAnalyzerIds()));
    return List.copyOf(values);
  }
}
