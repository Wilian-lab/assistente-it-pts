package com.wlilan.backend_assistent.Security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SetorSupport {

  private SetorSupport() {
  }

  public static String normalize(String value) {
    var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
        .replace('-', '_')
        .replace(' ', '_');

    return switch (normalized) {
      case "SECAGEM" -> "AGRI_PRODUCTS";
      case "AGRIPRODUCTS" -> "AGRI_PRODUCTS";
      default -> normalized;
    };
  }

  public static List<String> parseSetores(String raw) {
    return Arrays.stream(String.valueOf(raw == null ? "" : raw).split(","))
        .map(SetorSupport::normalize)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  public static String parseSetor(String raw) {
    return parseSetores(raw).stream().findFirst().orElse("");
  }

  public static boolean userHasSetor(String setor, String setorAtivo) {
    var wanted = normalize(setorAtivo);
    if (wanted.isBlank()) {
      return false;
    }

    return parseSetores(setor).contains(wanted);
  }

  public static Set<String> parseGroupedSetores(String raw) {
    return new LinkedHashSet<>(parseSetores(raw));
  }
}
