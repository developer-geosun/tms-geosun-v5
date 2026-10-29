package com.geosun.tms.freight.cost.repository;

import com.geosun.tms.freight.cost.domain.TollTariffSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TollTariffSetRepository extends JpaRepository<TollTariffSet, String> {
  List<TollTariffSet> findByEnterpriseIdAndActiveTrueOrderByNameAsc(String enterpriseId);

  List<TollTariffSet> findByEnterpriseIdOrderByNameAsc(String enterpriseId);

  Optional<TollTariffSet> findByIdAndEnterpriseId(String id, String enterpriseId);

  Optional<TollTariffSet> findByEnterpriseIdAndNameIgnoreCaseAndActiveTrue(
      String enterpriseId, String name);

  boolean existsByEnterpriseIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
      String enterpriseId, String name, String id);
}
