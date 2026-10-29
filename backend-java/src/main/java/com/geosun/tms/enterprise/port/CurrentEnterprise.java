package com.geosun.tms.enterprise.port;

import org.springframework.lang.NonNull;

/** Порт поточного підприємства: id береться з автентифікованого співробітника. */
public interface CurrentEnterprise {

  /** Id підприємства поточного користувача; без нього (оператор, анонім) — 403. */
  @NonNull
  String requireId();
}
