package com.donatomartin.sonarcli.core.util;

import com.donatomartin.sonarcli.core.model.IssueRecord;
import java.util.Comparator;

public final class IssueComparators {

  public static final Comparator<IssueRecord> ISSUE_ORDER = Comparator
    .comparing(IssueRecord::path)
    .thenComparingInt(IssueRecord::startLine)
    .thenComparingInt(IssueRecord::startLineOffset)
    .thenComparing(IssueRecord::selector);

  private IssueComparators() {}
}
