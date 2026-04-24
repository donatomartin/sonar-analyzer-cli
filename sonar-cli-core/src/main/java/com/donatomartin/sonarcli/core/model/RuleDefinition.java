package com.donatomartin.sonarcli.core.model;

import com.donatomartin.sonarcli.core.util.RuleSelectorSupport;
import java.util.List;

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
    return RuleSelectorSupport.matchesRequestedFamily(this, requestedFamily);
  }
}
