package com.wlilan.backend_assistent.Security;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SetorSupport {

  private SetorSupport() {
  }

  public static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
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
}
