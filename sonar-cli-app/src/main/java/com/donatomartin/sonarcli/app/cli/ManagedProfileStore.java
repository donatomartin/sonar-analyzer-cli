package com.donatomartin.sonarcli.app.cli;

import com.donatomartin.sonarcli.core.model.RuleConfigFile;
import com.donatomartin.sonarcli.core.util.RuleConfigSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ManagedProfileStore {

  public static final String CONFIG_DIR_PROPERTY = "sonar.analyzer.cli.configDir";
  public static final String CONFIG_DIR_ENV = "SONAR_ANALYZER_CLI_CONFIG_DIR";
  private static final Pattern PROFILE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

  private final Path configDir;
  private final Path profilesDir;
  private final Path currentProfileFile;

  public ManagedProfileStore(Path configDir) {
    this.configDir = configDir.toAbsolutePath().normalize();
    this.profilesDir = this.configDir.resolve("profiles");
    this.currentProfileFile = this.configDir.resolve("current-profile");
  }

  public static ManagedProfileStore defaultStore() {
    var configuredPath = System.getProperty(CONFIG_DIR_PROPERTY);
    if (configuredPath != null && !configuredPath.isBlank()) {
      return new ManagedProfileStore(Path.of(configuredPath));
    }
    var envPath = System.getenv(CONFIG_DIR_ENV);
    if (envPath != null && !envPath.isBlank()) {
      return new ManagedProfileStore(Path.of(envPath));
    }
    var xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
    if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
      return new ManagedProfileStore(Path.of(xdgConfigHome).resolve("sonar-analyzer-cli"));
    }
    var userHome = System.getProperty("user.home", ".").trim();
    return new ManagedProfileStore(Path.of(userHome).resolve(".config").resolve("sonar-analyzer-cli"));
  }

  public Path configDir() {
    return configDir;
  }

  public Path profilesDir() {
    return profilesDir;
  }

  public List<ManagedProfile> listProfiles() throws IOException {
    if (!Files.isDirectory(profilesDir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(profilesDir)) {
      return files
        .filter(Files::isRegularFile)
        .filter(path -> {
          var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
          return name.endsWith(".yaml") || name.endsWith(".yml");
        })
        .map(path -> new ManagedProfile(stripExtension(path.getFileName().toString()), path.toAbsolutePath().normalize()))
        .sorted(Comparator.comparing(ManagedProfile::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
    }
  }

  public List<String> listProfileNames() throws IOException {
    return listProfiles().stream().map(ManagedProfile::name).toList();
  }

  public boolean exists(String name) {
    var normalizedName = normalizeName(name, false);
    if (normalizedName == null) {
      return false;
    }
    return Files.isRegularFile(pathFor(normalizedName));
  }

  public RuleConfigFile load(String name) throws IOException {
    var normalizedName = normalizeName(name, true);
    var path = pathFor(normalizedName);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Unknown managed profile: " + normalizedName);
    }
    return RuleConfigSupport.read(path);
  }

  public void save(String name, RuleConfigFile config, boolean overwrite) throws IOException {
    var normalizedName = normalizeName(name, true);
    ensureConfigDirectories();
    var path = pathFor(normalizedName);
    if (!overwrite && Files.exists(path)) {
      throw new IllegalArgumentException("Managed profile already exists: " + normalizedName + ". Use --force to overwrite it.");
    }
    Files.writeString(
      path,
      RuleConfigSupport.toYaml(config),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    );
  }

  public void delete(String name) throws IOException {
    var normalizedName = normalizeName(name, true);
    var path = pathFor(normalizedName);
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Unknown managed profile: " + normalizedName);
    }
    Files.delete(path);
    var current = currentProfile();
    if (current.isPresent() && current.get().name().equals(normalizedName)) {
      clearCurrent();
    }
  }

  public void use(String name) throws IOException {
    var normalizedName = normalizeName(name, true);
    if (!exists(normalizedName)) {
      throw new IllegalArgumentException("Unknown managed profile: " + normalizedName);
    }
    ensureConfigDirectories();
    Files.writeString(
      currentProfileFile,
      normalizedName + System.lineSeparator(),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    );
  }

  public void clearCurrent() throws IOException {
    try {
      Files.deleteIfExists(currentProfileFile);
    } catch (NoSuchFileException ignored) {
      // Nothing to clear.
    }
  }

  public Optional<CurrentProfile> currentProfile() throws IOException {
    if (!Files.isRegularFile(currentProfileFile)) {
      return Optional.empty();
    }
    var name = Files.readString(currentProfileFile).trim();
    if (name.isBlank()) {
      return Optional.empty();
    }
    var normalizedName = normalizeName(name, false);
    if (normalizedName == null) {
      return Optional.of(new CurrentProfile(name, configDir.resolve("profiles").resolve(name + ".yaml"), false));
    }
    var path = pathFor(normalizedName);
    return Optional.of(new CurrentProfile(normalizedName, path, Files.isRegularFile(path)));
  }

  public Path resolveManagedProfile(String name) {
    var normalizedName = normalizeName(name, false);
    if (normalizedName == null) {
      return null;
    }
    var path = pathFor(normalizedName);
    return Files.isRegularFile(path) ? path : null;
  }

  public Path pathFor(String name) {
    var normalizedName = normalizeName(name, true);
    return profilesDir.resolve(normalizedName + ".yaml").toAbsolutePath().normalize();
  }

  private void ensureConfigDirectories() throws IOException {
    Files.createDirectories(profilesDir);
  }

  private static String normalizeName(String value, boolean failOnInvalid) {
    if (value == null) {
      if (failOnInvalid) {
        throw new IllegalArgumentException("Profile name is required.");
      }
      return null;
    }
    var normalized = value.trim();
    if (PROFILE_NAME_PATTERN.matcher(normalized).matches()) {
      return normalized;
    }
    if (failOnInvalid) {
      throw new IllegalArgumentException(
        "Invalid profile name: " + value + ". Use only letters, numbers, dot, underscore, or dash."
      );
    }
    return null;
  }

  private static String stripExtension(String filename) {
    int separator = filename.lastIndexOf('.');
    return separator > 0 ? filename.substring(0, separator) : filename;
  }

  public record ManagedProfile(String name, Path path) {}

  public record CurrentProfile(String name, Path path, boolean exists) {}
}
