package com.donatomartin.sonarcli.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonSupport {

  public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  public static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  private JsonSupport() {}
}
