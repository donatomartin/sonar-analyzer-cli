package com.donatomartin.sonarcli.core.util;

public final class TextSupport {

  private TextSupport() {}

  public static String stripHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return html
      .replaceAll("(?i)<br\\s*/?>", "\n")
      .replaceAll("(?i)</p>", "\n\n")
      .replaceAll("(?i)</li>", "\n")
      .replaceAll("<[^>]+>", "")
      .replace("&quot;", "\"")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&nbsp;", " ")
      .replaceAll("[ \\t]+", " ")
      .replaceAll("\\n{3,}", "\n\n")
      .trim();
  }
}
