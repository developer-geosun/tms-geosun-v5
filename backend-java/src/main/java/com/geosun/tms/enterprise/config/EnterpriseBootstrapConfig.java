package com.geosun.tms.enterprise.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  EnterpriseProperties.class,
  PlatformOperatorBootstrapProperties.class
})
public class EnterpriseBootstrapConfig {}
