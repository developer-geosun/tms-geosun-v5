package com.geosun.tms.reference.repository;

import com.geosun.tms.reference.domain.Driver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, String> {

  List<Driver> findByEnterpriseIdAndDeletedFalseOrderByLastNameAscFirstNameAsc(String enterpriseId);

  List<Driver> findByEnterpriseIdAndDeletedTrueOrderByLastNameAscFirstNameAsc(String enterpriseId);

  List<Driver> findByEnterpriseIdOrderByLastNameAscFirstNameAsc(String enterpriseId);

  boolean existsByEnterpriseIdAndLicenseNumberIgnoreCaseAndDeletedFalse(
      String enterpriseId, String licenseNumber);

  boolean existsByEnterpriseIdAndLicenseNumberIgnoreCaseAndDeletedFalseAndIdNot(
      String enterpriseId, String licenseNumber, String id);

  Optional<Driver> findByIdAndEnterpriseId(String id, String enterpriseId);

  Optional<Driver> findByIdAndEnterpriseIdAndDeletedFalse(String id, String enterpriseId);

  Optional<Driver> findByUserId(String userId);

  boolean existsByUserId(String userId);
}
