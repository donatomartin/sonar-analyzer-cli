package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.core.model.RuleConfigFile;
import com.donatomartin.sonarcli.core.util.RuleConfigSupport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedProfileStoreTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldSaveLoadUseResolveAndDeleteManagedProfiles() throws Exception {
    var store = new ManagedProfileStore(tempDir);
    var config = new RuleConfigFile(
      List.of("Sonar way"),
      List.of("typescript:S1186"),
      List.of("css:S1128")
    );

    store.save("frontend", config, false);

    assertTrue(store.exists("frontend"));
    assertEquals(RuleConfigSupport.normalize(config), store.load("frontend"));
    assertEquals(List.of("frontend"), store.listProfileNames());

    store.use("frontend");

    assertEquals("frontend", store.currentProfile().orElseThrow().name());
    assertEquals(store.pathFor("frontend"), Path.of(SonarAnalyzerCli.resolveRulesSelectionSource(null, store)));
    assertEquals(store.pathFor("frontend"), Path.of(SonarAnalyzerCli.resolveRulesSelectionSource("frontend", store)));

    store.delete("frontend");

    assertFalse(store.exists("frontend"));
    assertTrue(store.currentProfile().isEmpty());
  }

  @Test
  void shouldFailFastForMissingRulesFiles() {
    var store = new ManagedProfileStore(tempDir);

    var error = assertThrows(
      IllegalArgumentException.class,
      () -> SonarAnalyzerCli.resolveRulesSelectionSource("./missing-rules.yaml", store)
    );

    assertTrue(error.getMessage().contains("Rules file not found"));
  }
}
