package com.geosun.tms.routes.repository;

import com.geosun.tms.routes.domain.RouteRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

public interface RouteRequestRepository
    extends JpaRepository<RouteRequest, Long>, JpaSpecificationExecutor<RouteRequest> {
  boolean existsByRoute_Id(Long routeId);

  boolean existsByIdAndEnterpriseId(Long id, String enterpriseId);

  @EntityGraph(attributePaths = {"route", "user"})
  List<RouteRequest> findByEnterpriseIdAndUserIdOrderByCreatedAtDesc(
      String enterpriseId, String userId);

  @EntityGraph(attributePaths = {"route", "route.points", "user"})
  Optional<RouteRequest> findByIdAndEnterpriseIdAndUserId(
      Long id, String enterpriseId, String userId);

  @EntityGraph(attributePaths = {"route", "route.points", "user"})
  Optional<RouteRequest> findByIdAndEnterpriseId(Long id, String enterpriseId);

  @EntityGraph(attributePaths = {"route", "route.points", "user"})
  @NonNull
  Optional<RouteRequest> findById(@NonNull Long id);

  /** Унікальні email власників заявок підприємства — довідник для фільтра адмінки. */
  @Query(
      "select distinct u.email from RouteRequest r join r.user u"
          + " where u.email is not null and r.enterpriseId = :enterpriseId")
  List<String> findDistinctOwnerEmails(@Param("enterpriseId") String enterpriseId);
}
