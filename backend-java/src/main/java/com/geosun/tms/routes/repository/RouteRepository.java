package com.geosun.tms.routes.repository;

import com.geosun.tms.routes.domain.Route;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<Route, Long> {
  List<Route> findByEnterpriseIdAndUserIdAndDeletedFalseOrderByUpdatedAtDesc(
      String enterpriseId, String userId);

  List<Route> findByEnterpriseIdAndUserIdAndDeletedTrueOrderByUpdatedAtDesc(
      String enterpriseId, String userId);

  List<Route> findByEnterpriseIdAndUserIdOrderByUpdatedAtDesc(String enterpriseId, String userId);

  Optional<Route> findByIdAndEnterpriseIdAndUserIdAndDeletedFalse(
      Long id, String enterpriseId, String userId);

  /** Маршрут підприємства за id (адмін / розрахунок), без прив'язки до власника. */
  Optional<Route> findByIdAndEnterpriseId(Long id, String enterpriseId);

  @EntityGraph(attributePaths = "points")
  @Query(
      "select r from Route r where r.id = :id and r.enterpriseId = :enterpriseId"
          + " and r.user.id = :userId and r.deleted = false")
  Optional<Route> findByIdAndUserIdWithPoints(
      @Param("id") Long id,
      @Param("enterpriseId") String enterpriseId,
      @Param("userId") String userId);

  /** Маршрут власника незалежно від soft delete (для перегляду / restore). */
  @EntityGraph(attributePaths = "points")
  @Query(
      "select r from Route r where r.id = :id and r.enterpriseId = :enterpriseId"
          + " and r.user.id = :userId")
  Optional<Route> findByIdAndUserIdWithPointsIncludingDeleted(
      @Param("id") Long id,
      @Param("enterpriseId") String enterpriseId,
      @Param("userId") String userId);

  /**
   * Оновлює лише last_opened_at; не змінює updated_at (на відміну від зміни managed-сутності Route).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update Route r set r.lastOpenedAt = :openedAt where r.id = :id and r.user.id = :userId and"
          + " r.enterpriseId = :enterpriseId and r.deleted = false")
  int updateLastOpenedAt(
      @Param("id") Long id,
      @Param("enterpriseId") String enterpriseId,
      @Param("userId") String userId,
      @Param("openedAt") Instant openedAt);
}
