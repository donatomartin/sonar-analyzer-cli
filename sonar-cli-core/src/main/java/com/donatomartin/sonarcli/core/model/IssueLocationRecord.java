package com.donatomartin.sonarcli.core.model;

public record IssueLocationRecord(
  String path,
  Integer startLine,
  Integer startLineOffset,
  Integer endLine,
  Integer endLineOffset,
  String message
) {}
