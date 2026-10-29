package com.geosun.tms.enterprise.domain;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/** Код підприємства для входу: 2–32 символи, латиниця, цифри, дефіс. */
public final class EnterpriseCodeRules {

  public static final String RESERVED_PLATFORM = "platform";

  private static final Pattern CODE = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])$");

  private EnterpriseCodeRules() {}

  /** @return код у нижньому регістрі або null, якщо значення порожнє чи неприпустиме */
  @Nullable
  public static String normalize(@Nullable String raw) {
    if (raw == null) {
      return null;
    }
    String code = raw.trim().toLowerCase(Locale.ROOT);
    if (code.isEmpty() || RESERVED_PLATFORM.equals(code) || !CODE.matcher(code).matches()) {
      return null;
    }
    return code;
  }
}
