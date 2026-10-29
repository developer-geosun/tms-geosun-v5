package com.geosun.tms.reference.repository;

import com.geosun.tms.reference.domain.VehicleCombination;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleCombinationRepository extends JpaRepository<VehicleCombination, String> {

  List<VehicleCombination> findByEnterpriseIdAndDeletedFalseOrderByNameAscCreatedAtAsc(
      String enterpriseId);

  List<VehicleCombination> findByEnterpriseIdAndDeletedTrueOrderByNameAscCreatedAtAsc(
      String enterpriseId);

  List<VehicleCombination> findByEnterpriseIdOrderByNameAscCreatedAtAsc(String enterpriseId);

  boolean existsByEnterpriseIdAndTractor_IdAndTrailer_IdAndDeletedFalse(
      String enterpriseId, String tractorId, String trailerId);

  boolean existsByEnterpriseIdAndTractor_IdAndTrailer_IdAndDeletedFalseAndIdNot(
      String enterpriseId, String tractorId, String trailerId, String id);

  Optional<VehicleCombination> findByIdAndEnterpriseId(String id, String enterpriseId);

  Optional<VehicleCombination> findByIdAndEnterpriseIdAndDeletedFalse(
      String id, String enterpriseId);
}
