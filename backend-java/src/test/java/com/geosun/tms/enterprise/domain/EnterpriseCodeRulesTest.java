package com.geosun.tms.enterprise.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnterpriseCodeRulesTest {

  @Test
  void normalizesCaseAndAcceptsLettersDigitsHyphen() {
    assertThat(EnterpriseCodeRules.normalize(" Geo-Sun ")).isEqualTo("geo-sun");
  }

  @Test
  void rejectsShortReservedAndTrailingHyphen() {
    assertThat(EnterpriseCodeRules.normalize("a")).isNull();
    assertThat(EnterpriseCodeRules.normalize("platform")).isNull();
    assertThat(EnterpriseCodeRules.normalize("PLATFORM")).isNull();
    assertThat(EnterpriseCodeRules.normalize("geo-")).isNull();
    assertThat(EnterpriseCodeRules.normalize(null)).isNull();
  }
}
