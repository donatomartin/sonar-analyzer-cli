package com.donatomartin.sonarcli.core.model;

import java.util.List;
import java.util.Locale;

public record RuleDefinition(
  String analyzerId,
  RuleFamily family,
  String rawKey,
  String selector,
  String title,
  String descriptionHtml,
  String type,
  String defaultSeverity,
  List<String> tags,
  String ruleSpecification,
  List<String> languages,
  List<String> defaultQualityProfiles
) {
  public boolean inProfile(String profileName) {
    if (profileName == null || profileName.isBlank()) {
      return false;
    }
    return defaultQualityProfiles.stream().anyMatch(profile ->
      profile.equalsIgnoreCase(profileName.trim())
    );
  }

  public boolean belongsToAnalyzer(String id) {
    return analyzerId.equalsIgnoreCase(id);
  }

  public boolean matchesFamily(String requestedFamily) {
    return requestedFamily == null || requestedFamily.isBlank()
      || family.name().equalsIgnoreCase(requestedFamily)
      || family.selectorPrefix().equals(requestedFamily.toLowerCase(Locale.ROOT));
  }
}
