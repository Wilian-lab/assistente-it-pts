package com.wlilan.backend_assistent.assistant;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class AssistantTextSanitizer {

  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
  private static final List<String> MOJIBAKE_MARKERS = List.of(
      "Ã", "Â", "â", "ð", "�");

  private AssistantTextSanitizer() {
  }

  public static String sanitize(String value) {
    var sanitized = String.valueOf(value == null ? "" : value)
        .replace('\u0000', ' ')
        .replace('\u00A0', ' ')
        .trim();

    if (!looksMisencoded(sanitized)) {
      return sanitized;
    }

    var current = sanitized;
    for (int attempt = 0; attempt < 2; attempt += 1) {
      var repaired = bestCandidate(
          current,
          reencode(current, StandardCharsets.ISO_8859_1),
          reencode(current, WINDOWS_1252));
      if (repaired.equals(current)) {
        break;
      }
      current = repaired;
      if (!looksMisencoded(current)) {
        break;
      }
    }

    return current;
  }

  private static String reencode(String value, Charset sourceCharset) {
    try {
      return new String(value.getBytes(sourceCharset), StandardCharsets.UTF_8);
    } catch (Exception exception) {
      return value;
    }
  }

  private static String bestCandidate(String original, String... candidates) {
    var best = original;
    int bestScore = score(original);
    for (var candidate : candidates) {
      int candidateScore = score(candidate);
      if (candidateScore > bestScore) {
        best = candidate;
        bestScore = candidateScore;
      }
    }
    return best;
  }

  private static int score(String value) {
    var text = String.valueOf(value == null ? "" : value);
    int score = 0;
    score -= markerCount(text) * 6;
    score -= replacementCharCount(text) * 10;
    score += preferredCharCount(text) * 2;
    score += whitespaceCount(text);
    return score;
  }

  private static boolean looksMisencoded(String value) {
    return markerCount(value) > 0;
  }

  private static int markerCount(String value) {
    int count = 0;
    for (var marker : MOJIBAKE_MARKERS) {
      int index = value.indexOf(marker);
      while (index >= 0) {
        count += 1;
        index = value.indexOf(marker, index + marker.length());
      }
    }
    return count;
  }

  private static int replacementCharCount(String value) {
    int count = 0;
    for (int index = 0; index < value.length(); index += 1) {
      if (value.charAt(index) == '\uFFFD') {
        count += 1;
      }
    }
    return count;
  }

  private static int preferredCharCount(String value) {
    int count = 0;
    for (int index = 0; index < value.length(); index += 1) {
      char current = value.charAt(index);
      if (Character.isLetterOrDigit(current)
          || Character.isWhitespace(current)
          || ",.;:!?-_/()[]{}".indexOf(current) >= 0
          || "áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ".indexOf(current) >= 0) {
        count += 1;
      }
    }
    return count;
  }

  private static int whitespaceCount(String value) {
    int count = 0;
    for (int index = 0; index < value.length(); index += 1) {
      if (Character.isWhitespace(value.charAt(index))) {
        count += 1;
      }
    }
    return count;
  }
}
