package com.donatomartin.sonarcli.core.catalog;

import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.model.RuleFamily;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCatalogTest {

  @Test
  void shouldResolveCanonicalJavaScriptAndTypeScriptSelectors() {
    var catalog = new RuleCatalog(sampleRules());

    assertEquals("typescript:S6564", catalog.find("typescript:S6564").get(0).selector());
    assertEquals("javascript:S3776", catalog.find("javascript:S3776").get(0).selector());
    assertEquals("javascript:S3776", catalog.find("typescript:S3776").get(0).selector());
    assertTrue(catalog.find("S3776").isEmpty());
    assertEquals("javascript:S3776", catalog.findForIssue("S3776", "js").selector());
    assertEquals("java:S3776", catalog.findForIssue("S3776", "java").selector());
  }

  @Test
  void shouldKeepRecognizedRemoteOnlySelectorsAsWarnings() throws IOException {
    var catalog = new RuleCatalog(sampleRules());

    var selected = catalog.resolveSelection(
      null,
      List.of("tssecurity:S2083"),
      List.of("typescript:S6564"),
      Set.of("js")
    );

    assertTrue(selected.selectionWarnings().stream().anyMatch(warning -> warning.contains("Advanced Security injection rules")));
    assertTrue(selected.selectedRules().stream().noneMatch(rule -> rule.rawKey().equals("S6564")));
  }

  @Test
  void shouldRejectBareSelectorsWhenEnablingRules() {
    var catalog = new RuleCatalog(sampleRules());

    var error = assertThrows(
      IllegalArgumentException.class,
      () -> catalog.resolveSelection(null, List.of("S3776"), List.of(), Set.of("js", "java"))
    );

    assertTrue(error.getMessage().contains("Use a prefixed selector"));
  }

  private static List<RuleDefinition> sampleRules() {
    return List.of(
      new RuleDefinition(
        "js",
        RuleFamily.JS,
        "S3776",
        "S3776",
        "Cognitive complexity",
        "",
        "CODE_SMELL",
        "Critical",
        List.of(),
        "RSPEC-3776",
        List.of("js", "ts"),
        List.of("Sonar way")
      ),
      new RuleDefinition(
        "js",
        RuleFamily.JS,
        "S6564",
        "S6564",
        "Redundant type aliases",
        "",
        "CODE_SMELL",
        "Major",
        List.of(),
        "RSPEC-6564",
        List.of("ts"),
        List.of("Sonar way")
      ),
      new RuleDefinition(
        "java",
        RuleFamily.JAVA,
        "S3776",
        "S3776",
        "Cognitive complexity",
        "",
        "CODE_SMELL",
        "Critical",
        List.of(),
        "RSPEC-3776",
        List.of("java"),
        List.of("Sonar way")
      )
    );
  }
}
