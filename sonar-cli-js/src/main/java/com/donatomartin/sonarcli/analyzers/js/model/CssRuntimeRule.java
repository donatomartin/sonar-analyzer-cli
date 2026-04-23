package com.donatomartin.sonarcli.analyzers.js.model;

import java.util.List;

public record CssRuntimeRule(
  String rawKey,
  String stylelintKey,
  List<Object> configurations
) {}
