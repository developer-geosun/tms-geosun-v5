package com.geosun.tms.freight.cost.repository;

import com.geosun.tms.freight.cost.domain.FreightCostCalculation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreightCostCalculationRepository
    extends JpaRepository<FreightCostCalculation, String> {
  List<FreightCostCalculation> findByEnterpriseIdAndRouteRequest_IdOrderByCreatedAtDesc(
      String enterpriseId, Long routeRequestId);

  Optional<FreightCostCalculation> findByIdAndEnterpriseIdAndRouteRequest_Id(
      String id, String enterpriseId, Long routeRequestId);

  boolean existsByScenario_Id(String scenarioId);
}
