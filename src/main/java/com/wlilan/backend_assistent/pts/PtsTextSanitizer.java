package com.wlilan.backend_assistent.pts;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PtsTextSanitizer {

  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
  private static final List<String> MOJIBAKE_MARKERS = List.of("Ã", "Â", "â", "ð", "�");

  private PtsTextSanitizer() {
  }

  public static String sanitize(String value) {
    var input = String.valueOf(value == null ? "" : value)
        .replace('\u0000', ' ')
        .replace('\u00A0', ' ')
        .trim();

    if (!looksMisencoded(input)) {
      return input;
    }

    var current = input;
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

  public static boolean looksMisencoded(String value) {
    var input = String.valueOf(value == null ? "" : value);
    return MOJIBAKE_MARKERS.stream().anyMatch(input::contains);
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
    int markerPenalty = 0;
    for (var marker : MOJIBAKE_MARKERS) {
      int index = text.indexOf(marker);
      while (index >= 0) {
        markerPenalty += 1;
        index = text.indexOf(marker, index + marker.length());
      }
    }

    int preferredChars = 0;
    for (int index = 0; index < text.length(); index += 1) {
      char current = text.charAt(index);
      if (Character.isLetterOrDigit(current)
          || Character.isWhitespace(current)
          || ",.;:!?-_/()[]{}%".indexOf(current) >= 0
          || "áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ".indexOf(current) >= 0) {
        preferredChars += 1;
      }
    }

    return preferredChars - (markerPenalty * 6);
  }
}
