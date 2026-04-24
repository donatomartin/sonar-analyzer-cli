package com.donatomartin.sonarcli.core.catalog;

import com.donatomartin.sonarcli.core.model.RuleDefinition;
import com.donatomartin.sonarcli.core.model.RuleFamily;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCatalogTest {

  @Test
  void shouldResolveCanonicalJavaScriptAndTypeScriptSelectors() {
    var catalog = new RuleCatalog(sampleRules());

    assertEquals("typescript:S6564", catalog.find("typescript:S6564").get(0).selector());
    assertEquals("javascript:S3776", catalog.find("javascript:S3776").get(0).selector());
    assertEquals("javascript:S3776", catalog.find("typescript:S3776").get(0).selector());
    assertEquals(2, catalog.find("S3776").size());
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
