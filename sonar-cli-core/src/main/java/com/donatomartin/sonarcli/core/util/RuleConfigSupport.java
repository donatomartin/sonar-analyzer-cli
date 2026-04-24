package com.donatomartin.sonarcli.core.util;

import com.donatomartin.sonarcli.core.model.RuleConfigFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.Yaml;

public final class RuleConfigSupport {

  private RuleConfigSupport() {}

  public static RuleConfigFile read(Path file) throws IOException {
    var content = Files.readString(file);
    var filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
      Object loaded = new Yaml().load(content);
      if (!(loaded instanceof Map<?, ?> map)) {
        return RuleConfigFile.empty();
      }
      return new RuleConfigFile(
        normalizeList(coerceList(map.get("profiles"))),
        normalizeList(coerceList(map.get("enable"))),
        normalizeList(coerceList(map.get("disable")))
      );
    }
    var parsed = JsonSupport.GSON.fromJson(content, RuleConfigFile.class);
    return normalize(parsed);
  }

  public static RuleConfigFile normalize(RuleConfigFile config) {
    if (config == null) {
      return RuleConfigFile.empty();
    }
    return new RuleConfigFile(
      normalizeList(config.profiles()),
      normalizeList(config.enable()),
      normalizeList(config.disable())
    );
  }

  public static String toYaml(RuleConfigFile config) {
    var normalized = normalize(config);
    var builder = new StringBuilder();
    appendList(builder, "profiles", normalized.profiles());
    appendList(builder, "enable", normalized.enable());
    appendList(builder, "disable", normalized.disable());
    return builder.toString();
  }

  private static void appendList(StringBuilder builder, String name, List<String> values) {
    builder.append(name).append(':');
    if (values.isEmpty()) {
      builder.append(" []").append(System.lineSeparator());
      return;
    }
    builder.append(System.lineSeparator());
    for (String value : values) {
      builder.append("  - ").append(quoteYaml(value)).append(System.lineSeparator());
    }
  }

  private static String quoteYaml(String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  private static List<String> normalizeList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream()
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(value -> !value.isBlank())
      .toList();
  }

  private static List<String> coerceList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
    if (value instanceof String string) {
      return List.of(string);
    }
    return List.of(String.valueOf(value));
  }
}
