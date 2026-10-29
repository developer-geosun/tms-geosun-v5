package com.geosun.tms.enterprise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Облікові дані оператора платформи. Пароль лише для першого створення. */
@ConfigurationProperties(prefix = "app.bootstrap.platform-operator")
public class PlatformOperatorBootstrapProperties {

  private String email = "";
  private String password = "";

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email != null ? email : "";
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password != null ? password : "";
  }
}
