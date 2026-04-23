package com.donatomartin.sonarcli.analyzers.js.runtime;

import java.util.Map;
import java.util.Optional;
import org.sonar.api.config.Configuration;

public final class MapBackedConfiguration implements Configuration {

  private final Map<String, String> values;

  public MapBackedConfiguration(Map<String, String> values) {
    this.values = Map.copyOf(values);
  }

  @Override
  public Optional<String> get(String key) {
    return Optional.ofNullable(values.get(key));
  }

  @Override
  public boolean hasKey(String key) {
    return values.containsKey(key);
  }

  @Override
  public String[] getStringArray(String key) {
    var value = values.get(key);
    if (value == null || value.isBlank()) {
      return new String[0];
    }
    return value.split(",");
  }
}
