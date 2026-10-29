package com.geosun.tms.storage.repository;

import com.geosun.tms.storage.domain.StoredFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {

  List<StoredFile> findByEnterpriseIdOrderByCreatedAtDesc(String enterpriseId);

  List<StoredFile> findByEnterpriseIdAndStorageKeyStartingWithOrderByCreatedAtDesc(
      String enterpriseId, String prefix);

  Optional<StoredFile> findByIdAndEnterpriseId(String id, String enterpriseId);
}
