package com.donatomartin.sonarcli.app.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectRootResolverTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldPreferNearestProjectMarker() throws IOException {
    Files.writeString(tempDir.resolve("tsconfig.json"), "{}");
    Files.createDirectories(tempDir.resolve("src/nested"));
    Path file = tempDir.resolve("src/nested/example.ts");
    Files.writeString(file, "export const answer = 42;");

    assertEquals(tempDir, ProjectRootResolver.inferBaseDir(file));
  }
}
