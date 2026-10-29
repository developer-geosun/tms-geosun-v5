package com.geosun.tms.reference.repository;

import com.geosun.tms.reference.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

  List<Vehicle> findByEnterpriseIdAndDeletedFalseOrderByPlateNumberAsc(String enterpriseId);

  List<Vehicle> findByEnterpriseIdAndDeletedTrueOrderByPlateNumberAsc(String enterpriseId);

  List<Vehicle> findByEnterpriseIdOrderByPlateNumberAsc(String enterpriseId);

  boolean existsByEnterpriseIdAndPlateNumberIgnoreCaseAndDeletedFalseAndIdNot(
      String enterpriseId, String plateNumber, String id);

  boolean existsByEnterpriseIdAndPlateNumberIgnoreCaseAndDeletedFalse(
      String enterpriseId, String plateNumber);

  boolean existsByEnterpriseIdAndVinIgnoreCaseAndDeletedFalseAndIdNot(
      String enterpriseId, String vin, String id);

  boolean existsByEnterpriseIdAndVinIgnoreCaseAndDeletedFalse(String enterpriseId, String vin);

  boolean
      existsByEnterpriseIdAndRegistrationSeriesIgnoreCaseAndRegistrationNumberIgnoreCaseAndDeletedFalseAndIdNot(
          String enterpriseId, String series, String number, String id);

  boolean
      existsByEnterpriseIdAndRegistrationSeriesIgnoreCaseAndRegistrationNumberIgnoreCaseAndDeletedFalse(
          String enterpriseId, String series, String number);

  Optional<Vehicle> findByIdAndEnterpriseId(String id, String enterpriseId);

  Optional<Vehicle> findByIdAndEnterpriseIdAndDeletedFalse(String id, String enterpriseId);
}
