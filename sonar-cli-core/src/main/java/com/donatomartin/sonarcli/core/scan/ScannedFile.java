package com.donatomartin.sonarcli.core.scan;

import com.donatomartin.sonarcli.core.model.SourceType;
import java.nio.file.Path;

public record ScannedFile(
  Path path,
  String relativePath,
  SourceType sourceType,
  FileKind kind
) {}
