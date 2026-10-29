package com.geosun.tms.freight.cost.repository;

import com.geosun.tms.freight.cost.domain.FreightNumericScenario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreightNumericScenarioRepository
    extends JpaRepository<FreightNumericScenario, String> {
  List<FreightNumericScenario> findByEnterpriseIdAndActiveTrueOrderByNameAsc(String enterpriseId);

  List<FreightNumericScenario> findByEnterpriseIdOrderByNameAsc(String enterpriseId);

  Optional<FreightNumericScenario> findByIdAndEnterpriseId(String id, String enterpriseId);

  Optional<FreightNumericScenario> findByEnterpriseIdAndNameIgnoreCaseAndActiveTrue(
      String enterpriseId, String name);

  boolean existsByEnterpriseIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
      String enterpriseId, String name, String excludeId);

  boolean existsByTollTariffSet_IdAndActiveTrue(String tollTariffSetId);

  boolean existsByTollTariffSet_Id(String tollTariffSetId);
}
