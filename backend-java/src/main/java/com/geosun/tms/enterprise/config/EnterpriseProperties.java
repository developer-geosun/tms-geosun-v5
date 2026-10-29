package com.geosun.tms.enterprise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Код і назва першого підприємства з env. */
@ConfigurationProperties(prefix = "app.enterprise")
public class EnterpriseProperties {

  private String defaultCode = "";
  private String defaultName = "";

  public String getDefaultCode() {
    return defaultCode;
  }

  public void setDefaultCode(String defaultCode) {
    this.defaultCode = defaultCode != null ? defaultCode : "";
  }

  public String getDefaultName() {
    return defaultName;
  }

  public void setDefaultName(String defaultName) {
    this.defaultName = defaultName != null ? defaultName : "";
  }
}
