package com.donatomartin.sonarcli.app.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarAnalyzerCliTest {

  @TempDir
  Path tempDir;

  @AfterEach
  void clearConfigOverride() {
    System.clearProperty(ManagedProfileStore.CONFIG_DIR_PROPERTY);
  }

  @Test
  void shouldGenerateBashAndZshCompletionScripts() {
    System.setProperty(ManagedProfileStore.CONFIG_DIR_PROPERTY, tempDir.toString());

    var bash = execute("completion", "--shell", "bash");
    assertTrue(bash.contains("sonar-analyzer-cli Bash Completion"));
    assertTrue(bash.contains("profile"));
    assertTrue(bash.contains("completion"));
    assertTrue(bash.contains("--from-profile"));

    var zsh = execute("completion", "--shell", "zsh");
    assertTrue(zsh.startsWith("#compdef sonar-analyzer-cli"));
    assertTrue(zsh.contains("bashcompinit"));
  }

  @Test
  void shouldSaveAndUseManagedProfilesFromTheCli() throws Exception {
    System.setProperty(ManagedProfileStore.CONFIG_DIR_PROPERTY, tempDir.toString());

    var exitCode = executeWithExitCode("profile", "save", "frontend", "--enable", "javascript:S3776", "--use");
    assertEquals(0, exitCode);
    assertTrue(Files.isRegularFile(tempDir.resolve("profiles").resolve("frontend.yaml")));
    assertEquals("frontend", new ManagedProfileStore(tempDir).currentProfile().orElseThrow().name());
  }

  private static String execute(String... args) {
    var output = new ByteArrayOutputStream();
    var errors = new ByteArrayOutputStream();
    var commandLine = SonarAnalyzerCli.newCommandLine();
    commandLine.setOut(new PrintWriter(output, true));
    commandLine.setErr(new PrintWriter(errors, true));
    var originalOut = System.out;
    var originalErr = System.err;
    try {
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));
      var exitCode = commandLine.execute(args);
      assertEquals(0, exitCode, errors.toString(StandardCharsets.UTF_8));
      return output.toString(StandardCharsets.UTF_8);
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  private static int executeWithExitCode(String... args) {
    var output = new ByteArrayOutputStream();
    var errors = new ByteArrayOutputStream();
    var commandLine = SonarAnalyzerCli.newCommandLine();
    commandLine.setOut(new PrintWriter(output, true));
    commandLine.setErr(new PrintWriter(errors, true));
    var originalOut = System.out;
    var originalErr = System.err;
    try {
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));
      return commandLine.execute(args);
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }
}
