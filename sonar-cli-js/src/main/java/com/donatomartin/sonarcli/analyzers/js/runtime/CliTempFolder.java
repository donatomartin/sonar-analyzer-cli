package com.donatomartin.sonarcli.analyzers.js.runtime;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import javax.annotation.Nullable;
import org.sonar.api.utils.TempFolder;

public final class CliTempFolder implements TempFolder {

  @Override
  public File newDir() {
    return newDir("sonar-analyzer-cli");
  }

  @Override
  public File newDir(String name) {
    try {
      return Files.createTempDirectory(name).toFile();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public File newFile() {
    return newFile(null, null);
  }

  @Override
  public File newFile(@Nullable String prefix, @Nullable String suffix) {
    try {
      return Files.createTempFile(prefix, suffix).toFile();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
