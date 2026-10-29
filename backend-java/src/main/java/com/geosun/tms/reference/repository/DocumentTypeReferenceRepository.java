package com.geosun.tms.reference.repository;

import com.geosun.tms.reference.domain.DocumentTypeReference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTypeReferenceRepository
    extends JpaRepository<DocumentTypeReference, String> {

  Optional<DocumentTypeReference> findByIdAndEnterpriseId(String id, String enterpriseId);

  /** Не видалені типи підприємства — джерело копії для нового підприємства. */
  List<DocumentTypeReference> findByEnterpriseIdAndDeletedFalseOrderByCountryCodeAscNameUkAsc(
      String enterpriseId);

  /** Канонічні типи (id c2000000-…), якщо в джерельному підприємстві каталог порожній. */
  @Query(
      """
      SELECT d FROM DocumentTypeReference d
      WHERE d.deleted = false AND d.id LIKE 'c2000000-%'
      ORDER BY d.countryCode ASC, d.nameUk ASC
      """)
  List<DocumentTypeReference> findCanonicalActive();

  boolean existsByEnterpriseIdAndCountryCodeIgnoreCaseAndNameUkIgnoreCaseAndDeletedFalse(
      String enterpriseId, String countryCode, String nameUk);

  boolean existsByEnterpriseIdAndCountryCodeIgnoreCaseAndNameUkIgnoreCaseAndDeletedFalseAndIdNot(
      String enterpriseId, String countryCode, String nameUk, String id);

  @Query(
      """
      SELECT d FROM DocumentTypeReference d
      WHERE d.enterpriseId = :enterpriseId
        AND d.deleted = false
        AND (:country IS NULL OR :country = '' OR UPPER(d.countryCode) = UPPER(:country))
        AND (
          :search IS NULL OR :search = '' OR
          LOWER(d.nameUk) LIKE CONCAT('%', LOWER(:search), '%') OR
          LOWER(d.nameEn) LIKE CONCAT('%', LOWER(:search), '%') OR
          LOWER(d.nameRu) LIKE CONCAT('%', LOWER(:search), '%')
        )
      ORDER BY d.countryCode ASC, d.nameUk ASC
      """)
  List<DocumentTypeReference> searchActive(
      @Param("enterpriseId") String enterpriseId,
      @Param("search") String search,
      @Param("country") String country);

  @Query(
      """
      SELECT d FROM DocumentTypeReference d
      WHERE d.enterpriseId = :enterpriseId
        AND d.deleted = true
        AND (:country IS NULL OR :country = '' OR UPPER(d.countryCode) = UPPER(:country))
        AND (
          :search IS NULL OR :search = '' OR
          LOWER(d.nameUk) LIKE CONCAT('%', LOWER(:search), '%') OR
          LOWER(d.nameEn) LIKE CONCAT('%', LOWER(:search), '%') OR
          LOWER(d.nameRu) LIKE CONCAT('%', LOWER(:search), '%')
        )
      ORDER BY d.countryCode ASC, d.nameUk ASC
      """)
  List<DocumentTypeReference> searchDeleted(
      @Param("enterpriseId") String enterpriseId,
      @Param("search") String search,
      @Param("country") String country);

  @Query(
      """
      SELECT d FROM DocumentTypeReference d
      WHERE d.enterpriseId = :enterpriseId
        AND (:country IS NULL OR :country = '' OR UPPER(d.countryCode) = UPPER(:country))
        AND (
          :search IS NULL OR :search = '' OR
          LOWER(d.nameUk) LIKE CONCAT('%', LOWER(:search), '%') OR
          LOWER(d.nameEn) LIKE CONCAT('%', LOWER(:search), '%') OR
          LOWER(d.nameRu) LIKE CONCAT('%', LOWER(:search), '%')
        )
      ORDER BY d.countryCode ASC, d.nameUk ASC
      """)
  List<DocumentTypeReference> searchAll(
      @Param("enterpriseId") String enterpriseId,
      @Param("search") String search,
      @Param("country") String country);
}
