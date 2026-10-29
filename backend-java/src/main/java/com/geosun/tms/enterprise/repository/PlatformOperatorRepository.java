package com.geosun.tms.enterprise.repository;

import com.geosun.tms.enterprise.domain.PlatformOperator;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformOperatorRepository extends JpaRepository<PlatformOperator, String> {

  boolean existsByEmail(String email);

  Optional<PlatformOperator> findByEmail(String email);
}
