package com.geosun.tms.support;

import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Створює підприємство-фікстуру в профілі test (поза тестовою транзакцією). У профілі test
 * EnterpriseBootstrapRunner не запускається, тож без цього вхід за кодом не знаходить підприємство.
 */
@Component
@Profile("test")
@Order(10)
public class TestEnterpriseSeed implements ApplicationRunner {

  private final EnterpriseRepository enterpriseRepository;

  public TestEnterpriseSeed(EnterpriseRepository enterpriseRepository) {
    this.enterpriseRepository = enterpriseRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (enterpriseRepository.existsById(TestEnterprise.ID)) {
      return;
    }
    Enterprise enterprise = new Enterprise();
    enterprise.setId(TestEnterprise.ID);
    enterprise.setCode(TestEnterprise.CODE);
    enterprise.setName(TestEnterprise.NAME);
    enterprise.setActive(true);
    enterpriseRepository.saveAndFlush(enterprise);
  }
}
