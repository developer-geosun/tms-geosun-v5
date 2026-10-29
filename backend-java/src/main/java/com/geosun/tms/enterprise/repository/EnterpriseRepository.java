package com.geosun.tms.enterprise.repository;

import com.geosun.tms.enterprise.domain.Enterprise;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnterpriseRepository extends JpaRepository<Enterprise, String> {

  Optional<Enterprise> findFirstByOrderByCreatedAtAsc();

  Optional<Enterprise> findByCode(String code);

  boolean existsByCode(String code);

  List<Enterprise> findAllByOrderByCreatedAtDesc();
}
