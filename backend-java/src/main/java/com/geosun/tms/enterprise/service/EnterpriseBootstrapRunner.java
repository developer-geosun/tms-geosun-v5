package com.geosun.tms.enterprise.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Перенос існуючих рядків у перше підприємство. У профілі test не запускається: фікстури самі
 * викликають сервіс, порожній env не валить набір тестів.
 */
@Component
@Profile("!test")
@Order(50)
public class EnterpriseBootstrapRunner implements ApplicationRunner {

  private final EnterpriseBootstrapService bootstrapService;

  public EnterpriseBootstrapRunner(EnterpriseBootstrapService bootstrapService) {
    this.bootstrapService = bootstrapService;
  }

  @Override
  public void run(ApplicationArguments args) {
    bootstrapService.ensureFirstEnterprise();
  }
}
